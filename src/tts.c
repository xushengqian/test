/*
 * tts.c - TTS (Text-to-Speech) Interface Implementation
 *
 * This file implements the FreeSWITCH Speech/TTS interface for UniMRCP.
 *
 * Copyright (c) 2026
 * Licensed under the Apache License 2.0
 */

#include <switch.h>
#include "mrcp_client.h"
#include "mrcp_application.h"
#include "mrcp_message.h"
#include "mrcp_generic_header.h"
#include "mrcp_synth_header.h"
#include "mrcp_synth_resource.h"
#include "apt_log.h"

#include "mod_unimrcp.h"

/* Audio buffer configuration */
#define TTS_BUFFER_SIZE (1024 * 1024)  /* 1 MB */

/* Forward declarations */
static speech_channel_t *tts_channel_create(switch_speech_handle_t *sh,
                                             const char *voice_name,
                                             int rate,
                                             int channels,
                                             const char *profile_name);
static void tts_channel_destroy(speech_channel_t *schannel);
static switch_status_t tts_channel_speak(speech_channel_t *schannel, const char *text);
static switch_status_t tts_channel_stop(speech_channel_t *schannel);

/* TTS application message handler */
static apt_bool_t tts_app_message_handler(const mrcp_app_message_t *app_message);

/* Audio stream callback */
static apt_bool_t tts_stream_write(mpf_audio_stream_t *stream,
                                    const mpf_frame_t *frame);

/*
 * Open TTS/Speech interface
 */
switch_status_t unimrcp_speech_open(switch_speech_handle_t *sh,
                                     const char *voice_name,
                                     int rate,
                                     int channels,
                                     switch_speech_flag_t *flags)
{
    speech_channel_t *schannel;
    mod_unimrcp_globals_t *globals = unimrcp_get_globals();
    const char *profile_name;

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "TTS open: voice=%s, rate=%d, channels=%d\n",
        voice_name ? voice_name : "default", rate, channels);

    /* Determine profile to use */
    profile_name = globals->default_tts_profile;

    /* Create speech channel */
    schannel = tts_channel_create(sh, voice_name, rate, channels, profile_name);
    if (!schannel) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
            "Failed to create TTS channel\n");
        return SWITCH_STATUS_FALSE;
    }

    sh->private_info = schannel;

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO,
        "TTS channel opened: %s (voice=%s, profile=%s)\n",
        schannel->name, voice_name ? voice_name : "default", profile_name);

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Close TTS/Speech interface
 */
switch_status_t unimrcp_speech_close(switch_speech_handle_t *sh,
                                      switch_speech_flag_t *flags)
{
    speech_channel_t *schannel = (speech_channel_t *)sh->private_info;

    if (!schannel) {
        return SWITCH_STATUS_SUCCESS;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "TTS close: channel=%s\n", schannel->name);

    /* Stop synthesis if in progress */
    if (schannel->state == SPEECH_CHANNEL_PROCESSING) {
        tts_channel_stop(schannel);
    }

    /* Destroy the channel */
    tts_channel_destroy(schannel);
    sh->private_info = NULL;

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO,
        "TTS channel closed\n");

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Feed text to TTS for synthesis
 */
switch_status_t unimrcp_speech_feed_tts(switch_speech_handle_t *sh,
                                         char *text,
                                         switch_speech_flag_t *flags)
{
    speech_channel_t *schannel = (speech_channel_t *)sh->private_info;

    if (!schannel || !text) {
        return SWITCH_STATUS_FALSE;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "TTS feed: channel=%s, text=%s\n", schannel->name, text);

    /* Clear the audio buffer */
    switch_mutex_lock(schannel->mutex);
    if (schannel->audio_buffer) {
        switch_buffer_zero(schannel->audio_buffer);
    }
    switch_mutex_unlock(schannel->mutex);

    /* Start synthesis */
    return tts_channel_speak(schannel, text);
}

/*
 * Read synthesized audio from TTS
 */
switch_status_t unimrcp_speech_read_tts(switch_speech_handle_t *sh,
                                         void *data,
                                         switch_size_t *datalen,
                                         switch_speech_flag_t *flags)
{
    speech_channel_t *schannel = (speech_channel_t *)sh->private_info;
    switch_size_t bytes_read = 0;

    if (!schannel || !data || !datalen || *datalen == 0) {
        return SWITCH_STATUS_FALSE;
    }

    switch_mutex_lock(schannel->mutex);

    if (schannel->audio_buffer) {
        /* Read available audio data */
        bytes_read = switch_buffer_read(schannel->audio_buffer, data, *datalen);
    }

    switch_mutex_unlock(schannel->mutex);

    if (bytes_read == 0) {
        /* No audio available */
        if (schannel->state == SPEECH_CHANNEL_DONE ||
            schannel->state == SPEECH_CHANNEL_ERROR) {
            /* Synthesis complete, no more data */
            *datalen = 0;
            return SWITCH_STATUS_BREAK;
        }
        /* Still synthesizing, return silence */
        memset(data, 0, *datalen);
        return SWITCH_STATUS_SUCCESS;
    }

    *datalen = bytes_read;
    return SWITCH_STATUS_SUCCESS;
}

/*
 * Flush TTS audio buffer
 */
void unimrcp_speech_flush_tts(switch_speech_handle_t *sh)
{
    speech_channel_t *schannel = (speech_channel_t *)sh->private_info;

    if (!schannel) {
        return;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "TTS flush: channel=%s\n", schannel->name);

    /* Stop any ongoing synthesis */
    tts_channel_stop(schannel);

    /* Clear the audio buffer */
    switch_mutex_lock(schannel->mutex);
    if (schannel->audio_buffer) {
        switch_buffer_zero(schannel->audio_buffer);
    }
    switch_mutex_unlock(schannel->mutex);
}

/*
 * Set text parameter for TTS
 */
void unimrcp_speech_text_param_tts(switch_speech_handle_t *sh, char *param, const char *val)
{
    speech_channel_t *schannel = (speech_channel_t *)sh->private_info;

    if (!schannel || !param) {
        return;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "TTS text param: channel=%s, param=%s, val=%s\n",
        schannel->name, param, val ? val : "null");

    /* Handle known parameters */
    if (!strcasecmp(param, "voice") || !strcasecmp(param, "voice-name")) {
        if (val) {
            schannel->voice_name = switch_core_strdup(schannel->pool, val);
        }
    } else if (!strcasecmp(param, "language") || !strcasecmp(param, "voice-language")) {
        if (val) {
            schannel->voice_language = switch_core_strdup(schannel->pool, val);
        }
    } else {
        /* Store in params hash */
        if (!schannel->params) {
            switch_core_hash_init(&schannel->params);
        }
        if (val) {
            switch_core_hash_insert(schannel->params, param,
                                    switch_core_strdup(schannel->pool, val));
        }
    }
}

/*
 * Set numeric parameter for TTS
 */
void unimrcp_speech_numeric_param_tts(switch_speech_handle_t *sh, char *param, int val)
{
    speech_channel_t *schannel = (speech_channel_t *)sh->private_info;
    char buf[32];

    if (!schannel || !param) {
        return;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "TTS numeric param: channel=%s, param=%s, val=%d\n",
        schannel->name, param, val);

    switch_snprintf(buf, sizeof(buf), "%d", val);

    if (!schannel->params) {
        switch_core_hash_init(&schannel->params);
    }

    switch_core_hash_insert(schannel->params, param,
                            switch_core_strdup(schannel->pool, buf));
}

/*
 * Set float parameter for TTS
 */
void unimrcp_speech_float_param_tts(switch_speech_handle_t *sh, char *param, double val)
{
    speech_channel_t *schannel = (speech_channel_t *)sh->private_info;
    char buf[64];

    if (!schannel || !param) {
        return;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "TTS float param: channel=%s, param=%s, val=%f\n",
        schannel->name, param, val);

    /* Handle known parameters */
    if (!strcasecmp(param, "rate") || !strcasecmp(param, "speech-rate")) {
        schannel->speech_rate = (float)val;
    } else if (!strcasecmp(param, "volume")) {
        schannel->volume = (float)val;
    }

    switch_snprintf(buf, sizeof(buf), "%f", val);

    if (!schannel->params) {
        switch_core_hash_init(&schannel->params);
    }

    switch_core_hash_insert(schannel->params, param,
                            switch_core_strdup(schannel->pool, buf));
}

/*
 * Create TTS speech channel
 */
static speech_channel_t *tts_channel_create(switch_speech_handle_t *sh,
                                             const char *voice_name,
                                             int rate,
                                             int channels,
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
    schannel = switch_core_alloc(sh->memory_pool, sizeof(*schannel));
    if (!schannel) {
        return NULL;
    }
    memset(schannel, 0, sizeof(*schannel));

    schannel->pool = sh->memory_pool;
    schannel->type = SPEECH_CHANNEL_TTS;
    schannel->state = SPEECH_CHANNEL_CLOSED;
    schannel->profile = profile;
    schannel->speech_rate = 1.0f;
    schannel->volume = 1.0f;

    /* Store voice name */
    if (voice_name) {
        schannel->voice_name = switch_core_strdup(schannel->pool, voice_name);
    }

    /* Generate unique channel name */
    switch_uuid_get(&uuid);
    switch_uuid_format(uuid_str, &uuid);
    schannel->name = switch_core_strdup(schannel->pool, uuid_str);

    /* Initialize mutex and condition variable */
    switch_mutex_init(&schannel->mutex, SWITCH_MUTEX_NESTED, schannel->pool);
    switch_thread_cond_create(&schannel->cond, schannel->pool);

    /* Create audio buffer */
    if (switch_buffer_create_dynamic(&schannel->audio_buffer,
                                      TTS_BUFFER_SIZE / 4,
                                      TTS_BUFFER_SIZE / 2,
                                      TTS_BUFFER_SIZE) != SWITCH_STATUS_SUCCESS) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
            "Failed to create audio buffer\n");
        return NULL;
    }

    /* Create MRCP session */
    if (globals->mrcp_client) {
        mrcp_application_t *app;
        mrcp_session_t *session;

        /* Get or create application for this profile */
        app = mrcp_application_create(tts_app_message_handler, schannel, globals->pool);
        if (!app) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "Failed to create MRCP application\n");
            switch_buffer_destroy(&schannel->audio_buffer);
            return NULL;
        }

        /* Create session */
        session = mrcp_application_session_create(app, profile->name, schannel);
        if (!session) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "Failed to create MRCP session\n");
            switch_buffer_destroy(&schannel->audio_buffer);
            return NULL;
        }

        schannel->application = app;
        schannel->session = session;

        /* Add synthesizer channel */
        mrcp_channel_t *channel;
        mpf_termination_t *termination;
        mpf_rtp_termination_descriptor_t *rtp_descriptor;
        mpf_stream_capabilities_t *capabilities;

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

        /* Create stream capabilities */
        capabilities = mpf_sink_stream_capabilities_create(
            mrcp_application_session_pool_get(session));
        mpf_codec_capabilities_add(
            &capabilities->codecs,
            MPF_SAMPLE_RATE_8000 | MPF_SAMPLE_RATE_16000,
            "LPCM");

        /* Create termination */
        termination = mrcp_application_sink_termination_create(
            session, capabilities, rtp_descriptor);

        /* Add synthesizer channel */
        channel = mrcp_application_channel_create(
            session, MRCP_SYNTHESIZER_RESOURCE, termination, NULL, schannel);

        if (!channel) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "Failed to create MRCP synthesizer channel\n");
            mrcp_application_session_terminate(session);
            switch_buffer_destroy(&schannel->audio_buffer);
            return NULL;
        }

        /* Add channel to session */
        if (mrcp_application_channel_add(session, channel) != TRUE) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "Failed to add MRCP synthesizer channel\n");
            mrcp_application_session_terminate(session);
            switch_buffer_destroy(&schannel->audio_buffer);
            return NULL;
        }

        /* Wait for channel to be ready */
        if (speech_channel_wait_for_state(schannel, SPEECH_CHANNEL_READY,
                                           globals->request_timeout) != SWITCH_STATUS_SUCCESS) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "Timeout waiting for TTS channel to be ready\n");
            mrcp_application_session_terminate(session);
            switch_buffer_destroy(&schannel->audio_buffer);
            return NULL;
        }
    }

    return schannel;
}

/*
 * Destroy TTS speech channel
 */
static void tts_channel_destroy(speech_channel_t *schannel)
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

    /* Destroy audio buffer */
    if (schannel->audio_buffer) {
        switch_buffer_destroy(&schannel->audio_buffer);
    }

    /* Clean up params hash */
    if (schannel->params) {
        switch_core_hash_destroy(&schannel->params);
        schannel->params = NULL;
    }

    speech_channel_set_state(schannel, SPEECH_CHANNEL_CLOSED);
}

/*
 * Start speech synthesis
 */
static switch_status_t tts_channel_speak(speech_channel_t *schannel, const char *text)
{
    mrcp_message_t *mrcp_message;
    mrcp_synth_header_t *synth_header;
    mrcp_generic_header_t *generic_header;
    const char *content_type = "text/plain";

    if (!schannel || !schannel->session || !schannel->unimrcp_channel) {
        return SWITCH_STATUS_FALSE;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "Starting synthesis: channel=%s\n", schannel->name);

    /* Create SPEAK request */
    mrcp_message = mrcp_application_message_create(
        schannel->session, schannel->unimrcp_channel, SYNTHESIZER_SPEAK);
    if (!mrcp_message) {
        return SWITCH_STATUS_FALSE;
    }

    /* Set generic headers */
    generic_header = (mrcp_generic_header_t *)mrcp_generic_header_prepare(mrcp_message);
    if (generic_header) {
        /* Determine content type based on text format */
        if (text) {
            if (strstr(text, "<?xml") || strstr(text, "<speak")) {
                content_type = "application/ssml+xml";
            }
        }
        apt_string_set(&generic_header->content_type, content_type);
        mrcp_generic_header_property_add(mrcp_message, GENERIC_HEADER_CONTENT_TYPE);
    }

    /* Set synthesizer-specific headers */
    synth_header = (mrcp_synth_header_t *)mrcp_resource_header_prepare(mrcp_message);
    if (synth_header) {
        /* Set voice name if specified */
        if (schannel->voice_name) {
            apt_str_set(&synth_header->voice_param.name, schannel->voice_name);
            mrcp_resource_header_property_add(mrcp_message, SYNTHESIZER_HEADER_VOICE_NAME);
        }

        /* Set voice language if specified */
        if (schannel->voice_language) {
            apt_str_set(&synth_header->speech_language, schannel->voice_language);
            mrcp_resource_header_property_add(mrcp_message, SYNTHESIZER_HEADER_SPEECH_LANGUAGE);
        }

        /* Set speech rate if modified */
        if (schannel->speech_rate != 1.0f) {
            synth_header->prosody_param.rate.type = PROSODY_RATE_TYPE_RELATIVE;
            synth_header->prosody_param.rate.value.relative = schannel->speech_rate;
            mrcp_resource_header_property_add(mrcp_message, SYNTHESIZER_HEADER_PROSODY_RATE);
        }

        /* Set volume if modified */
        if (schannel->volume != 1.0f) {
            synth_header->prosody_param.volume.type = PROSODY_VOLUME_TYPE_RELATIVE;
            synth_header->prosody_param.volume.value.relative = schannel->volume;
            mrcp_resource_header_property_add(mrcp_message, SYNTHESIZER_HEADER_PROSODY_VOLUME);
        }

        /* Set additional parameters from hash */
        if (schannel->params) {
            const char *val;

            val = switch_core_hash_find(schannel->params, "kill-on-barge-in");
            if (val && switch_true(val)) {
                synth_header->kill_on_barge_in = TRUE;
                mrcp_resource_header_property_add(mrcp_message, SYNTHESIZER_HEADER_KILL_ON_BARGE_IN);
            }
        }
    }

    /* Set text as message body */
    if (text) {
        apt_str_set(&mrcp_message->body, text);
    }

    /* Send the SPEAK request */
    if (mrcp_application_message_send(schannel->session, schannel->unimrcp_channel,
                                       mrcp_message) != TRUE) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
            "Failed to send SPEAK request: channel=%s\n", schannel->name);
        return SWITCH_STATUS_FALSE;
    }

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Stop speech synthesis
 */
static switch_status_t tts_channel_stop(speech_channel_t *schannel)
{
    mrcp_message_t *mrcp_message;

    if (!schannel || !schannel->session || !schannel->unimrcp_channel) {
        return SWITCH_STATUS_FALSE;
    }

    if (schannel->state != SPEECH_CHANNEL_PROCESSING) {
        return SWITCH_STATUS_SUCCESS;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "Stopping synthesis: channel=%s\n", schannel->name);

    /* Create STOP request */
    mrcp_message = mrcp_application_message_create(
        schannel->session, schannel->unimrcp_channel, SYNTHESIZER_STOP);
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
 * TTS application message handler
 */
static apt_bool_t tts_app_message_handler(const mrcp_app_message_t *app_message)
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
                    "TTS session update response: channel=%s, status=%d\n",
                    schannel->name, app_message->sig_message.status);
                break;
            case MRCP_SIG_COMMAND_SESSION_TERMINATE:
                switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                    "TTS session terminate response: channel=%s\n", schannel->name);
                speech_channel_set_state(schannel, SPEECH_CHANNEL_CLOSED);
                break;
            case MRCP_SIG_COMMAND_CHANNEL_ADD:
                if (app_message->sig_message.status == MRCP_SIG_STATUS_CODE_SUCCESS) {
                    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                        "TTS channel added: channel=%s\n", schannel->name);
                    schannel->unimrcp_channel = app_message->channel;
                    speech_channel_set_state(schannel, SPEECH_CHANNEL_READY);
                } else {
                    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                        "TTS channel add failed: channel=%s\n", schannel->name);
                    speech_channel_set_state(schannel, SPEECH_CHANNEL_ERROR);
                }
                break;
            case MRCP_SIG_COMMAND_CHANNEL_REMOVE:
                switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                    "TTS channel removed: channel=%s\n", schannel->name);
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
                if (message->start_line.method_id == SYNTHESIZER_SPEAK) {
                    if (message->start_line.request_state == MRCP_REQUEST_STATE_INPROGRESS) {
                        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                            "Synthesis in progress: channel=%s\n", schannel->name);
                        speech_channel_set_state(schannel, SPEECH_CHANNEL_PROCESSING);
                    } else if (message->start_line.status_code != MRCP_STATUS_CODE_SUCCESS) {
                        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                            "Synthesis failed: channel=%s, status=%d\n",
                            schannel->name, message->start_line.status_code);
                        speech_channel_set_state(schannel, SPEECH_CHANNEL_ERROR);
                    }
                } else if (message->start_line.method_id == SYNTHESIZER_STOP) {
                    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                        "Synthesis stopped: channel=%s\n", schannel->name);
                    speech_channel_set_state(schannel, SPEECH_CHANNEL_READY);
                }
            } else if (message->start_line.message_type == MRCP_MESSAGE_TYPE_EVENT) {
                if (message->start_line.method_id == SYNTHESIZER_SPEAK_COMPLETE) {
                    mrcp_synth_header_t *synth_header;
                    synth_header = (mrcp_synth_header_t *)mrcp_resource_header_get(message);

                    if (synth_header) {
                        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                            "Synthesis complete: channel=%s, cause=%d\n",
                            schannel->name, synth_header->completion_cause);
                    }
                    speech_channel_set_state(schannel, SPEECH_CHANNEL_DONE);
                }
            }
        }
        break;
    default:
        break;
    }

    return TRUE;
}

/*
 * Audio stream write callback - receives audio from MRCP server
 */
static apt_bool_t tts_stream_write(mpf_audio_stream_t *stream,
                                    const mpf_frame_t *frame)
{
    speech_channel_t *schannel;

    if (!stream || !frame) {
        return FALSE;
    }

    schannel = mpf_audio_stream_descriptor_get(stream)->sink.data;
    if (!schannel) {
        return FALSE;
    }

    /* Only process audio frames */
    if (frame->type != MEDIA_FRAME_TYPE_AUDIO) {
        return TRUE;
    }

    /* Write audio to buffer */
    if (schannel->audio_buffer && frame->codec_frame.buffer &&
        frame->codec_frame.size > 0) {
        switch_mutex_lock(schannel->mutex);
        switch_buffer_write(schannel->audio_buffer,
                           frame->codec_frame.buffer,
                           frame->codec_frame.size);
        switch_mutex_unlock(schannel->mutex);
    }

    return TRUE;
}

/* EOF */
