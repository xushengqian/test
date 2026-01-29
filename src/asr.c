/*
 * asr.c - ASR (Automatic Speech Recognition) Interface Implementation
 *
 * This file implements the FreeSWITCH ASR interface for UniMRCP.
 *
 * Copyright (c) 2026
 * Licensed under the Apache License 2.0
 */

#include <switch.h>
#include "mrcp_client.h"
#include "mrcp_application.h"
#include "mrcp_message.h"
#include "mrcp_generic_header.h"
#include "mrcp_recog_header.h"
#include "mrcp_recog_resource.h"
#include "apt_log.h"

#include "mod_unimrcp.h"

/* Forward declarations */
static speech_channel_t *asr_channel_create(switch_asr_handle_t *ah,
                                             const char *codec,
                                             int rate,
                                             const char *profile_name);
static void asr_channel_destroy(speech_channel_t *schannel);
static switch_status_t asr_channel_start_recognition(speech_channel_t *schannel,
                                                      const char *grammar);
static switch_status_t asr_channel_stop_recognition(speech_channel_t *schannel);

/* ASR application message handler */
static apt_bool_t asr_app_message_handler(const mrcp_app_message_t *app_message);

/* ASR application callbacks */
static const mrcp_app_message_dispatcher_t asr_app_dispatcher = {
    NULL, /* on_session_update */
    NULL, /* on_session_terminate */
    NULL, /* on_channel_add */
    NULL, /* on_channel_remove */
    NULL, /* on_message_receive */
    NULL, /* on_terminate_event */
    NULL  /* on_resource_discover */
};

/*
 * Open ASR interface
 */
switch_status_t unimrcp_asr_open(switch_asr_handle_t *ah,
                                  const char *codec,
                                  int rate,
                                  const char *dest,
                                  switch_asr_flag_t *flags)
{
    speech_channel_t *schannel;
    mod_unimrcp_globals_t *globals = unimrcp_get_globals();
    const char *profile_name;

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR open: codec=%s, rate=%d, dest=%s\n",
        codec ? codec : "none", rate, dest ? dest : "none");

    /* Determine profile to use */
    profile_name = dest ? dest : globals->default_asr_profile;

    /* Create speech channel */
    schannel = asr_channel_create(ah, codec, rate, profile_name);
    if (!schannel) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
            "Failed to create ASR channel\n");
        return SWITCH_STATUS_FALSE;
    }

    ah->private_info = schannel;

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO,
        "ASR channel opened: %s (profile=%s)\n", schannel->name, profile_name);

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Close ASR interface
 */
switch_status_t unimrcp_asr_close(switch_asr_handle_t *ah,
                                   switch_asr_flag_t *flags)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;

    if (!schannel) {
        return SWITCH_STATUS_SUCCESS;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR close: channel=%s\n", schannel->name);

    /* Stop recognition if in progress */
    if (schannel->state == SPEECH_CHANNEL_PROCESSING) {
        asr_channel_stop_recognition(schannel);
    }

    /* Destroy the channel */
    asr_channel_destroy(schannel);
    ah->private_info = NULL;

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO,
        "ASR channel closed\n");

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Load grammar for ASR
 */
switch_status_t unimrcp_asr_load_grammar(switch_asr_handle_t *ah,
                                          const char *grammar,
                                          const char *name)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;

    if (!schannel) {
        return SWITCH_STATUS_FALSE;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR load grammar: channel=%s, grammar=%s, name=%s\n",
        schannel->name, grammar ? grammar : "none", name ? name : "none");

    /* Store the grammar */
    switch_mutex_lock(schannel->mutex);
    if (schannel->grammar) {
        switch_safe_free(schannel->grammar);
    }
    schannel->grammar = switch_core_strdup(schannel->pool, grammar);
    switch_mutex_unlock(schannel->mutex);

    /* Start recognition with this grammar */
    return asr_channel_start_recognition(schannel, grammar);
}

/*
 * Unload grammar from ASR
 */
switch_status_t unimrcp_asr_unload_grammar(switch_asr_handle_t *ah,
                                            const char *name)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;

    if (!schannel) {
        return SWITCH_STATUS_FALSE;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR unload grammar: channel=%s, name=%s\n",
        schannel->name, name ? name : "none");

    /* Stop recognition */
    return asr_channel_stop_recognition(schannel);
}

/*
 * Feed audio data to ASR
 */
switch_status_t unimrcp_asr_feed(switch_asr_handle_t *ah,
                                  void *data,
                                  unsigned int len,
                                  switch_asr_flag_t *flags)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;
    mpf_frame_t frame;

    if (!schannel || !data || len == 0) {
        return SWITCH_STATUS_FALSE;
    }

    /* Check if we're in a state to receive audio */
    if (schannel->state != SPEECH_CHANNEL_PROCESSING) {
        return SWITCH_STATUS_SUCCESS;
    }

    /* Send audio to MRCP server via the media stream */
    if (schannel->unimrcp_channel) {
        mrcp_application_source_frame_get(schannel->unimrcp_channel, &frame);
        if (frame.codec_frame.buffer && len <= frame.codec_frame.size) {
            memcpy(frame.codec_frame.buffer, data, len);
            frame.codec_frame.size = len;
            frame.type = MEDIA_FRAME_TYPE_AUDIO;
            /* Frame will be sent automatically by the media processing thread */
        }
    }

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Resume ASR
 */
switch_status_t unimrcp_asr_resume(switch_asr_handle_t *ah)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;

    if (!schannel) {
        return SWITCH_STATUS_FALSE;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR resume: channel=%s\n", schannel->name);

    /* If we have a grammar loaded, restart recognition */
    if (schannel->grammar) {
        return asr_channel_start_recognition(schannel, schannel->grammar);
    }

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Pause ASR
 */
switch_status_t unimrcp_asr_pause(switch_asr_handle_t *ah)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;

    if (!schannel) {
        return SWITCH_STATUS_FALSE;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR pause: channel=%s\n", schannel->name);

    return asr_channel_stop_recognition(schannel);
}

/*
 * Check for ASR results
 */
switch_status_t unimrcp_asr_check_results(switch_asr_handle_t *ah,
                                           switch_asr_flag_t *flags)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;
    switch_status_t status = SWITCH_STATUS_FALSE;

    if (!schannel) {
        return SWITCH_STATUS_FALSE;
    }

    switch_mutex_lock(schannel->mutex);
    if (schannel->state == SPEECH_CHANNEL_DONE && schannel->result) {
        status = SWITCH_STATUS_SUCCESS;
        switch_set_flag(ah, SWITCH_ASR_FLAG_RESULT);
    } else if (schannel->state == SPEECH_CHANNEL_ERROR) {
        status = SWITCH_STATUS_FALSE;
    } else if (schannel->state == SPEECH_CHANNEL_PROCESSING) {
        status = SWITCH_STATUS_BREAK;
    }
    switch_mutex_unlock(schannel->mutex);

    return status;
}

/*
 * Get ASR results
 */
switch_status_t unimrcp_asr_get_results(switch_asr_handle_t *ah,
                                         char **xmlstr,
                                         switch_asr_flag_t *flags)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;

    if (!schannel || !xmlstr) {
        return SWITCH_STATUS_FALSE;
    }

    switch_mutex_lock(schannel->mutex);
    if (schannel->result) {
        *xmlstr = switch_core_strdup(ah->memory_pool, schannel->result);
        switch_safe_free(schannel->result);
        schannel->result = NULL;
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
            "ASR get results: channel=%s, result=%s\n",
            schannel->name, *xmlstr);
    } else {
        *xmlstr = NULL;
    }
    switch_mutex_unlock(schannel->mutex);

    /* Reset state for next recognition */
    speech_channel_set_state(schannel, SPEECH_CHANNEL_READY);

    return *xmlstr ? SWITCH_STATUS_SUCCESS : SWITCH_STATUS_FALSE;
}

/*
 * Start input timers
 */
switch_status_t unimrcp_asr_start_input_timers(switch_asr_handle_t *ah)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;
    mrcp_message_t *mrcp_message;

    if (!schannel || !schannel->unimrcp_channel) {
        return SWITCH_STATUS_FALSE;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR start input timers: channel=%s\n", schannel->name);

    /* Create START-INPUT-TIMERS request */
    mrcp_message = mrcp_application_message_create(
        schannel->session, schannel->unimrcp_channel, RECOGNIZER_START_INPUT_TIMERS);
    if (!mrcp_message) {
        return SWITCH_STATUS_FALSE;
    }

    /* Send the message */
    if (mrcp_application_message_send(schannel->session, schannel->unimrcp_channel,
                                       mrcp_message) != TRUE) {
        return SWITCH_STATUS_FALSE;
    }

    schannel->start_input_timers = 1;

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Set text parameter for ASR
 */
void unimrcp_asr_text_param(switch_asr_handle_t *ah, char *param, const char *val)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;

    if (!schannel || !param) {
        return;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR text param: channel=%s, param=%s, val=%s\n",
        schannel->name, param, val ? val : "null");

    if (!schannel->params) {
        switch_core_hash_init(&schannel->params);
    }

    if (val) {
        switch_core_hash_insert(schannel->params, param,
                                switch_core_strdup(schannel->pool, val));
    }
}

/*
 * Set numeric parameter for ASR
 */
void unimrcp_asr_numeric_param(switch_asr_handle_t *ah, char *param, int val)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;
    char buf[32];

    if (!schannel || !param) {
        return;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR numeric param: channel=%s, param=%s, val=%d\n",
        schannel->name, param, val);

    switch_snprintf(buf, sizeof(buf), "%d", val);

    if (!schannel->params) {
        switch_core_hash_init(&schannel->params);
    }

    switch_core_hash_insert(schannel->params, param,
                            switch_core_strdup(schannel->pool, buf));
}

/*
 * Set float parameter for ASR
 */
void unimrcp_asr_float_param(switch_asr_handle_t *ah, char *param, double val)
{
    speech_channel_t *schannel = (speech_channel_t *)ah->private_info;
    char buf[64];

    if (!schannel || !param) {
        return;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR float param: channel=%s, param=%s, val=%f\n",
        schannel->name, param, val);

    switch_snprintf(buf, sizeof(buf), "%f", val);

    if (!schannel->params) {
        switch_core_hash_init(&schannel->params);
    }

    switch_core_hash_insert(schannel->params, param,
                            switch_core_strdup(schannel->pool, buf));
}

/*
 * Create ASR speech channel
 */
static speech_channel_t *asr_channel_create(switch_asr_handle_t *ah,
                                             const char *codec,
                                             int rate,
                                             const char *profile_name)
{
    speech_channel_t *schannel;
    mod_unimrcp_globals_t *globals = unimrcp_get_globals();
    mrcp_profile_t *profile;
    switch_uuid_t uuid;
    char uuid_str[SWITCH_UUID_FORMATTED_LENGTH + 1];

    /* Find the profile */
    profile = unimrcp_get_profile(profile_name);
    if (!profile) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
            "MRCP profile '%s' not found\n", profile_name);
        return NULL;
    }

    /* Allocate speech channel */
    schannel = switch_core_alloc(ah->memory_pool, sizeof(*schannel));
    if (!schannel) {
        return NULL;
    }
    memset(schannel, 0, sizeof(*schannel));

    schannel->pool = ah->memory_pool;
    schannel->type = SPEECH_CHANNEL_ASR;
    schannel->state = SPEECH_CHANNEL_CLOSED;
    schannel->profile = profile;

    /* Generate unique channel name */
    switch_uuid_get(&uuid);
    switch_uuid_format(uuid_str, &uuid);
    schannel->name = switch_core_strdup(schannel->pool, uuid_str);

    /* Initialize mutex and condition variable */
    switch_mutex_init(&schannel->mutex, SWITCH_MUTEX_NESTED, schannel->pool);
    switch_thread_cond_create(&schannel->cond, schannel->pool);

    /* Create MRCP session */
    if (globals->mrcp_client) {
        mrcp_application_t *app;
        mrcp_session_t *session;

        /* Get or create application for this profile */
        app = mrcp_application_create(asr_app_message_handler, schannel, globals->pool);
        if (!app) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "Failed to create MRCP application\n");
            return NULL;
        }

        /* Create session */
        session = mrcp_application_session_create(app, profile->name, schannel);
        if (!session) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "Failed to create MRCP session\n");
            return NULL;
        }

        schannel->application = app;
        schannel->session = session;

        /* Add recognizer channel */
        mrcp_channel_t *channel;
        mpf_termination_t *termination;
        mpf_rtp_termination_descriptor_t *rtp_descriptor;

        /* Create RTP termination descriptor */
        rtp_descriptor = apr_palloc(mrcp_application_session_pool_get(session),
                                     sizeof(mpf_rtp_termination_descriptor_t));
        mpf_rtp_termination_descriptor_init(rtp_descriptor);

        /* Set RTP settings from profile */
        if (profile->rtp_ip) {
            apt_str_set(&rtp_descriptor->audio.local.ip, profile->rtp_ip);
        }
        rtp_descriptor->audio.local.port_min = profile->rtp_port_min;
        rtp_descriptor->audio.local.port_max = profile->rtp_port_max;

        /* Create termination */
        termination = mrcp_application_source_termination_create(
            session, NULL, rtp_descriptor);

        /* Add recognizer channel */
        channel = mrcp_application_channel_create(
            session, MRCP_RECOGNIZER_RESOURCE, termination, NULL, schannel);

        if (!channel) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "Failed to create MRCP recognizer channel\n");
            mrcp_application_session_terminate(session);
            return NULL;
        }

        /* Add channel to session */
        if (mrcp_application_channel_add(session, channel) != TRUE) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "Failed to add MRCP recognizer channel\n");
            mrcp_application_session_terminate(session);
            return NULL;
        }

        /* Wait for channel to be ready */
        if (speech_channel_wait_for_state(schannel, SPEECH_CHANNEL_READY,
                                           globals->request_timeout) != SWITCH_STATUS_SUCCESS) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "Timeout waiting for ASR channel to be ready\n");
            mrcp_application_session_terminate(session);
            return NULL;
        }
    }

    return schannel;
}

/*
 * Destroy ASR speech channel
 */
static void asr_channel_destroy(speech_channel_t *schannel)
{
    if (!schannel) {
        return;
    }

    /* Terminate MRCP session */
    if (schannel->session) {
        if (schannel->unimrcp_channel) {
            mrcp_application_channel_remove(schannel->session, schannel->unimrcp_channel);
        }
        mrcp_application_session_terminate(schannel->session);
        schannel->session = NULL;
    }

    /* Clean up grammar */
    if (schannel->grammar) {
        switch_safe_free(schannel->grammar);
        schannel->grammar = NULL;
    }

    /* Clean up result */
    if (schannel->result) {
        switch_safe_free(schannel->result);
        schannel->result = NULL;
    }

    /* Clean up params hash */
    if (schannel->params) {
        switch_core_hash_destroy(&schannel->params);
        schannel->params = NULL;
    }

    speech_channel_set_state(schannel, SPEECH_CHANNEL_CLOSED);
}

/*
 * Start recognition on ASR channel
 */
static switch_status_t asr_channel_start_recognition(speech_channel_t *schannel,
                                                      const char *grammar)
{
    mrcp_message_t *mrcp_message;
    mrcp_recog_header_t *recog_header;
    mrcp_generic_header_t *generic_header;
    const char *content_type = "application/srgs+xml";
    const char *content_id = NULL;

    if (!schannel || !schannel->session || !schannel->unimrcp_channel) {
        return SWITCH_STATUS_FALSE;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "Starting recognition: channel=%s\n", schannel->name);

    /* Create RECOGNIZE request */
    mrcp_message = mrcp_application_message_create(
        schannel->session, schannel->unimrcp_channel, RECOGNIZER_RECOGNIZE);
    if (!mrcp_message) {
        return SWITCH_STATUS_FALSE;
    }

    /* Set generic headers */
    generic_header = (mrcp_generic_header_t *)mrcp_generic_header_prepare(mrcp_message);
    if (generic_header) {
        /* Determine content type based on grammar format */
        if (grammar) {
            if (strstr(grammar, "http://") || strstr(grammar, "https://") ||
                strstr(grammar, "file://") || strstr(grammar, "builtin:")) {
                /* URI reference - set content-type to text/uri-list */
                content_type = "text/uri-list";
            } else if (strstr(grammar, "<?xml") || strstr(grammar, "<grammar")) {
                content_type = "application/srgs+xml";
            } else if (strstr(grammar, "#JSGF")) {
                content_type = "application/x-jsgf";
            }
        }
        apt_string_set(&generic_header->content_type, content_type);
        mrcp_generic_header_property_add(mrcp_message, GENERIC_HEADER_CONTENT_TYPE);
    }

    /* Set recognizer-specific headers */
    recog_header = (mrcp_recog_header_t *)mrcp_resource_header_prepare(mrcp_message);
    if (recog_header) {
        /* Set confidence threshold */
        recog_header->confidence_threshold = 0.5f;
        mrcp_resource_header_property_add(mrcp_message, RECOGNIZER_HEADER_CONFIDENCE_THRESHOLD);

        /* Set start/end input timers if not already started */
        if (!schannel->start_input_timers) {
            recog_header->start_input_timers = TRUE;
            mrcp_resource_header_property_add(mrcp_message, RECOGNIZER_HEADER_START_INPUT_TIMERS);
        }

        /* Set speech timeouts from parameters if available */
        if (schannel->params) {
            const char *val;

            val = switch_core_hash_find(schannel->params, "no-input-timeout");
            if (val) {
                recog_header->no_input_timeout = atoi(val);
                mrcp_resource_header_property_add(mrcp_message, RECOGNIZER_HEADER_NO_INPUT_TIMEOUT);
            }

            val = switch_core_hash_find(schannel->params, "recognition-timeout");
            if (val) {
                recog_header->recognition_timeout = atoi(val);
                mrcp_resource_header_property_add(mrcp_message, RECOGNIZER_HEADER_RECOGNITION_TIMEOUT);
            }

            val = switch_core_hash_find(schannel->params, "speech-complete-timeout");
            if (val) {
                recog_header->speech_complete_timeout = atoi(val);
                mrcp_resource_header_property_add(mrcp_message, RECOGNIZER_HEADER_SPEECH_COMPLETE_TIMEOUT);
            }

            val = switch_core_hash_find(schannel->params, "speech-incomplete-timeout");
            if (val) {
                recog_header->speech_incomplete_timeout = atoi(val);
                mrcp_resource_header_property_add(mrcp_message, RECOGNIZER_HEADER_SPEECH_INCOMPLETE_TIMEOUT);
            }
        }
    }

    /* Set grammar as message body */
    if (grammar) {
        apt_str_set(&mrcp_message->body, grammar);
    }

    /* Send the RECOGNIZE request */
    if (mrcp_application_message_send(schannel->session, schannel->unimrcp_channel,
                                       mrcp_message) != TRUE) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
            "Failed to send RECOGNIZE request: channel=%s\n", schannel->name);
        return SWITCH_STATUS_FALSE;
    }

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Stop recognition on ASR channel
 */
static switch_status_t asr_channel_stop_recognition(speech_channel_t *schannel)
{
    mrcp_message_t *mrcp_message;

    if (!schannel || !schannel->session || !schannel->unimrcp_channel) {
        return SWITCH_STATUS_FALSE;
    }

    if (schannel->state != SPEECH_CHANNEL_PROCESSING) {
        return SWITCH_STATUS_SUCCESS;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "Stopping recognition: channel=%s\n", schannel->name);

    /* Create STOP request */
    mrcp_message = mrcp_application_message_create(
        schannel->session, schannel->unimrcp_channel, RECOGNIZER_STOP);
    if (!mrcp_message) {
        return SWITCH_STATUS_FALSE;
    }

    /* Send the STOP request */
    if (mrcp_application_message_send(schannel->session, schannel->unimrcp_channel,
                                       mrcp_message) != TRUE) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
            "Failed to send STOP request: channel=%s\n", schannel->name);
        return SWITCH_STATUS_FALSE;
    }

    return SWITCH_STATUS_SUCCESS;
}

/*
 * ASR application message handler
 */
static apt_bool_t asr_app_message_handler(const mrcp_app_message_t *app_message)
{
    speech_channel_t *schannel;

    if (!app_message) {
        return FALSE;
    }

    schannel = mrcp_application_session_object_get(app_message->session);
    if (!schannel) {
        return FALSE;
    }

    switch (app_message->message_type) {
    case MRCP_APP_MESSAGE_TYPE_SIGNALING:
        switch (app_message->sig_message.message_type) {
        case MRCP_SIG_MESSAGE_TYPE_RESPONSE:
            switch (app_message->sig_message.command_id) {
            case MRCP_SIG_COMMAND_SESSION_UPDATE:
                switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                    "ASR session update response: channel=%s, status=%d\n",
                    schannel->name, app_message->sig_message.status);
                break;
            case MRCP_SIG_COMMAND_SESSION_TERMINATE:
                switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                    "ASR session terminate response: channel=%s\n", schannel->name);
                speech_channel_set_state(schannel, SPEECH_CHANNEL_CLOSED);
                break;
            case MRCP_SIG_COMMAND_CHANNEL_ADD:
                if (app_message->sig_message.status == MRCP_SIG_STATUS_CODE_SUCCESS) {
                    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                        "ASR channel added: channel=%s\n", schannel->name);
                    schannel->unimrcp_channel = app_message->channel;
                    speech_channel_set_state(schannel, SPEECH_CHANNEL_READY);
                } else {
                    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                        "ASR channel add failed: channel=%s\n", schannel->name);
                    speech_channel_set_state(schannel, SPEECH_CHANNEL_ERROR);
                }
                break;
            case MRCP_SIG_COMMAND_CHANNEL_REMOVE:
                switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                    "ASR channel removed: channel=%s\n", schannel->name);
                schannel->unimrcp_channel = NULL;
                break;
            default:
                break;
            }
            break;
        case MRCP_SIG_MESSAGE_TYPE_EVENT:
            break;
        default:
            break;
        }
        break;
    case MRCP_APP_MESSAGE_TYPE_CONTROL:
        /* Handle MRCP control messages (responses and events) */
        if (app_message->control_message) {
            mrcp_message_t *message = app_message->control_message;

            if (message->start_line.message_type == MRCP_MESSAGE_TYPE_RESPONSE) {
                if (message->start_line.method_id == RECOGNIZER_RECOGNIZE) {
                    if (message->start_line.request_state == MRCP_REQUEST_STATE_INPROGRESS) {
                        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                            "Recognition in progress: channel=%s\n", schannel->name);
                        speech_channel_set_state(schannel, SPEECH_CHANNEL_PROCESSING);
                    } else if (message->start_line.status_code != MRCP_STATUS_CODE_SUCCESS) {
                        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                            "Recognition failed: channel=%s, status=%d\n",
                            schannel->name, message->start_line.status_code);
                        speech_channel_set_state(schannel, SPEECH_CHANNEL_ERROR);
                    }
                }
            } else if (message->start_line.message_type == MRCP_MESSAGE_TYPE_EVENT) {
                if (message->start_line.method_id == RECOGNIZER_RECOGNITION_COMPLETE) {
                    mrcp_recog_header_t *recog_header;
                    recog_header = (mrcp_recog_header_t *)mrcp_resource_header_get(message);

                    if (recog_header) {
                        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                            "Recognition complete: channel=%s, cause=%d\n",
                            schannel->name, recog_header->completion_cause);

                        if (recog_header->completion_cause == RECOGNIZER_COMPLETION_CAUSE_SUCCESS) {
                            if (message->body.buf && message->body.length > 0) {
                                switch_mutex_lock(schannel->mutex);
                                schannel->result = switch_core_strdup(schannel->pool,
                                    message->body.buf);
                                switch_mutex_unlock(schannel->mutex);
                            }
                        }
                    }
                    speech_channel_set_state(schannel, SPEECH_CHANNEL_DONE);
                } else if (message->start_line.method_id == RECOGNIZER_START_OF_INPUT) {
                    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                        "Start of input detected: channel=%s\n", schannel->name);
                }
            }
        }
        break;
    default:
        break;
    }

    return TRUE;
}

/* EOF */
