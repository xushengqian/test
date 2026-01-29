/*
 * mod_unimrcp.c - FreeSWITCH UniMRCP Module
 *
 * This module provides MRCP (Media Resource Control Protocol) client
 * functionality for FreeSWITCH, enabling:
 * - ASR (Automatic Speech Recognition)
 * - TTS (Text-to-Speech synthesis)
 *
 * Copyright (c) 2026
 * Licensed under the Apache License 2.0
 */

#include <switch.h>
#include <apr_general.h>
#include <apr_pools.h>

#include "mrcp_client.h"
#include "mrcp_application.h"
#include "mrcp_message.h"
#include "mrcp_generic_header.h"
#include "mrcp_synth_header.h"
#include "mrcp_synth_resource.h"
#include "mrcp_recog_header.h"
#include "mrcp_recog_resource.h"
#include "unimrcp_client.h"
#include "apt_log.h"

#include "mod_unimrcp.h"

/* Module definition */
SWITCH_MODULE_LOAD_FUNCTION(mod_unimrcp_load);
SWITCH_MODULE_SHUTDOWN_FUNCTION(mod_unimrcp_shutdown);
SWITCH_MODULE_DEFINITION(mod_unimrcp, mod_unimrcp_load, mod_unimrcp_shutdown, NULL);

/* Global module configuration */
static mod_unimrcp_globals_t globals;

/* Memory pool for APR */
static apr_pool_t *mod_pool = NULL;

/* Function prototypes */
static switch_status_t do_config(void);
static switch_status_t create_mrcp_client(void);
static void destroy_mrcp_client(void);

/* ASR application functions */
static apt_bool_t asr_message_handler(const mrcp_app_message_t *app_message);
static apt_bool_t asr_on_session_update(mrcp_application_t *application, mrcp_session_t *session, mrcp_sig_status_code_e status);
static apt_bool_t asr_on_session_terminate(mrcp_application_t *application, mrcp_session_t *session, mrcp_sig_status_code_e status);
static apt_bool_t asr_on_channel_add(mrcp_application_t *application, mrcp_session_t *session, mrcp_channel_t *channel, mrcp_sig_status_code_e status);
static apt_bool_t asr_on_channel_remove(mrcp_application_t *application, mrcp_session_t *session, mrcp_channel_t *channel, mrcp_sig_status_code_e status);
static apt_bool_t asr_on_message_receive(mrcp_application_t *application, mrcp_session_t *session, mrcp_channel_t *channel, mrcp_message_t *message);

/* TTS application functions */
static apt_bool_t tts_message_handler(const mrcp_app_message_t *app_message);
static apt_bool_t tts_on_session_update(mrcp_application_t *application, mrcp_session_t *session, mrcp_sig_status_code_e status);
static apt_bool_t tts_on_session_terminate(mrcp_application_t *application, mrcp_session_t *session, mrcp_sig_status_code_e status);
static apt_bool_t tts_on_channel_add(mrcp_application_t *application, mrcp_session_t *session, mrcp_channel_t *channel, mrcp_sig_status_code_e status);
static apt_bool_t tts_on_channel_remove(mrcp_application_t *application, mrcp_session_t *session, mrcp_channel_t *channel, mrcp_sig_status_code_e status);
static apt_bool_t tts_on_message_receive(mrcp_application_t *application, mrcp_session_t *session, mrcp_channel_t *channel, mrcp_message_t *message);

/* Application message handlers */
static const mrcp_app_message_dispatcher_t asr_dispatcher = {
    asr_on_session_update,
    asr_on_session_terminate,
    asr_on_channel_add,
    asr_on_channel_remove,
    asr_on_message_receive,
    NULL, /* on_terminate_event */
    NULL  /* on_resource_discover */
};

static const mrcp_app_message_dispatcher_t tts_dispatcher = {
    tts_on_session_update,
    tts_on_session_terminate,
    tts_on_channel_add,
    tts_on_channel_remove,
    tts_on_message_receive,
    NULL, /* on_terminate_event */
    NULL  /* on_resource_discover */
};

/*
 * Module Load Function
 */
SWITCH_MODULE_LOAD_FUNCTION(mod_unimrcp_load)
{
    switch_api_interface_t *api_interface;
    switch_asr_interface_t *asr_interface;
    switch_speech_interface_t *speech_interface;

    /* Initialize globals */
    memset(&globals, 0, sizeof(globals));
    globals.pool = pool;
    switch_mutex_init(&globals.mutex, SWITCH_MUTEX_NESTED, pool);

    /* Initialize APR pool */
    if (apr_initialize() != APR_SUCCESS) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, "Failed to initialize APR\n");
        return SWITCH_STATUS_GENERR;
    }

    if (apr_pool_create(&mod_pool, NULL) != APR_SUCCESS) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, "Failed to create APR pool\n");
        apr_terminate();
        return SWITCH_STATUS_GENERR;
    }

    /* Load configuration */
    if (do_config() != SWITCH_STATUS_SUCCESS) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, "Failed to load configuration\n");
        apr_pool_destroy(mod_pool);
        apr_terminate();
        return SWITCH_STATUS_GENERR;
    }

    /* Create MRCP client */
    if (create_mrcp_client() != SWITCH_STATUS_SUCCESS) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, "Failed to create MRCP client\n");
        apr_pool_destroy(mod_pool);
        apr_terminate();
        return SWITCH_STATUS_GENERR;
    }

    /* Connect the module to FreeSWITCH */
    *module_interface = switch_loadable_module_create_module_interface(pool, modname);

    /* Register ASR interface */
    asr_interface = switch_loadable_module_create_interface(*module_interface, SWITCH_ASR_INTERFACE);
    asr_interface->interface_name = "unimrcp";
    asr_interface->asr_open = unimrcp_asr_open;
    asr_interface->asr_load_grammar = unimrcp_asr_load_grammar;
    asr_interface->asr_unload_grammar = unimrcp_asr_unload_grammar;
    asr_interface->asr_close = unimrcp_asr_close;
    asr_interface->asr_feed = unimrcp_asr_feed;
    asr_interface->asr_resume = unimrcp_asr_resume;
    asr_interface->asr_pause = unimrcp_asr_pause;
    asr_interface->asr_check_results = unimrcp_asr_check_results;
    asr_interface->asr_get_results = unimrcp_asr_get_results;
    asr_interface->asr_start_input_timers = unimrcp_asr_start_input_timers;
    asr_interface->asr_text_param = unimrcp_asr_text_param;
    asr_interface->asr_numeric_param = unimrcp_asr_numeric_param;
    asr_interface->asr_float_param = unimrcp_asr_float_param;

    /* Register TTS/Speech interface */
    speech_interface = switch_loadable_module_create_interface(*module_interface, SWITCH_SPEECH_INTERFACE);
    speech_interface->interface_name = "unimrcp";
    speech_interface->speech_open = unimrcp_speech_open;
    speech_interface->speech_close = unimrcp_speech_close;
    speech_interface->speech_feed_tts = unimrcp_speech_feed_tts;
    speech_interface->speech_read_tts = unimrcp_speech_read_tts;
    speech_interface->speech_flush_tts = unimrcp_speech_flush_tts;
    speech_interface->speech_text_param_tts = unimrcp_speech_text_param_tts;
    speech_interface->speech_numeric_param_tts = unimrcp_speech_numeric_param_tts;
    speech_interface->speech_float_param_tts = unimrcp_speech_float_param_tts;

    /* Register API commands */
    SWITCH_ADD_API(api_interface, "unimrcp", "UniMRCP API", unimrcp_api_function, UNIMRCP_API_SYNTAX);

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO, "mod_unimrcp loaded successfully\n");

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Module Shutdown Function
 */
SWITCH_MODULE_SHUTDOWN_FUNCTION(mod_unimrcp_shutdown)
{
    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO, "mod_unimrcp shutting down...\n");

    /* Destroy MRCP client */
    destroy_mrcp_client();

    /* Cleanup APR */
    if (mod_pool) {
        apr_pool_destroy(mod_pool);
        mod_pool = NULL;
    }
    apr_terminate();

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO, "mod_unimrcp shutdown complete\n");

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Load configuration from unimrcp.conf.xml
 */
static switch_status_t do_config(void)
{
    switch_xml_t cfg, xml, settings, param, profiles, profile;
    const char *var, *val;

    /* Set default values */
    globals.default_asr_profile = "default";
    globals.default_tts_profile = "default";
    globals.log_level = SWITCH_LOG_DEBUG;
    globals.max_connection_count = 100;
    globals.offer_new_connection = 1;
    globals.request_timeout = 30000;

    if (!(xml = switch_xml_open_cfg("unimrcp.conf", &cfg, NULL))) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, "Open of unimrcp.conf failed\n");
        return SWITCH_STATUS_GENERR;
    }

    /* Parse settings */
    if ((settings = switch_xml_child(cfg, "settings"))) {
        for (param = switch_xml_child(settings, "param"); param; param = param->next) {
            var = switch_xml_attr_soft(param, "name");
            val = switch_xml_attr_soft(param, "value");

            if (!strcasecmp(var, "default-asr-profile")) {
                globals.default_asr_profile = switch_core_strdup(globals.pool, val);
            } else if (!strcasecmp(var, "default-tts-profile")) {
                globals.default_tts_profile = switch_core_strdup(globals.pool, val);
            } else if (!strcasecmp(var, "log-level")) {
                globals.log_level = switch_log_str2level(val);
            } else if (!strcasecmp(var, "max-connection-count")) {
                globals.max_connection_count = atoi(val);
            } else if (!strcasecmp(var, "offer-new-connection")) {
                globals.offer_new_connection = switch_true(val);
            } else if (!strcasecmp(var, "request-timeout")) {
                globals.request_timeout = atoi(val);
            }
        }
    }

    /* Parse profiles */
    if ((profiles = switch_xml_child(cfg, "profiles"))) {
        for (profile = switch_xml_child(profiles, "profile"); profile; profile = profile->next) {
            mrcp_profile_t *mrcp_profile;
            const char *name = switch_xml_attr_soft(profile, "name");

            if (zstr(name)) {
                continue;
            }

            mrcp_profile = switch_core_alloc(globals.pool, sizeof(*mrcp_profile));
            memset(mrcp_profile, 0, sizeof(*mrcp_profile));
            mrcp_profile->name = switch_core_strdup(globals.pool, name);

            /* Default values */
            mrcp_profile->server_ip = "127.0.0.1";
            mrcp_profile->server_port = 8060;
            mrcp_profile->client_ip = "0.0.0.0";
            mrcp_profile->client_port = 0;
            mrcp_profile->mrcp_version = 2;
            mrcp_profile->codec = "PCMU";
            mrcp_profile->sample_rate = 8000;

            for (param = switch_xml_child(profile, "param"); param; param = param->next) {
                var = switch_xml_attr_soft(param, "name");
                val = switch_xml_attr_soft(param, "value");

                if (!strcasecmp(var, "server-ip")) {
                    mrcp_profile->server_ip = switch_core_strdup(globals.pool, val);
                } else if (!strcasecmp(var, "server-port")) {
                    mrcp_profile->server_port = atoi(val);
                } else if (!strcasecmp(var, "client-ip")) {
                    mrcp_profile->client_ip = switch_core_strdup(globals.pool, val);
                } else if (!strcasecmp(var, "client-port")) {
                    mrcp_profile->client_port = atoi(val);
                } else if (!strcasecmp(var, "mrcp-version")) {
                    mrcp_profile->mrcp_version = atoi(val);
                } else if (!strcasecmp(var, "codec")) {
                    mrcp_profile->codec = switch_core_strdup(globals.pool, val);
                } else if (!strcasecmp(var, "sample-rate")) {
                    mrcp_profile->sample_rate = atoi(val);
                } else if (!strcasecmp(var, "sip-transport")) {
                    mrcp_profile->sip_transport = switch_core_strdup(globals.pool, val);
                } else if (!strcasecmp(var, "rtp-ip")) {
                    mrcp_profile->rtp_ip = switch_core_strdup(globals.pool, val);
                } else if (!strcasecmp(var, "rtp-port-min")) {
                    mrcp_profile->rtp_port_min = atoi(val);
                } else if (!strcasecmp(var, "rtp-port-max")) {
                    mrcp_profile->rtp_port_max = atoi(val);
                }
            }

            /* Add profile to hash */
            if (!globals.profiles) {
                switch_core_hash_init(&globals.profiles);
            }
            switch_core_hash_insert(globals.profiles, mrcp_profile->name, mrcp_profile);

            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO,
                "Loaded MRCP profile: %s (server=%s:%d, version=%d)\n",
                mrcp_profile->name, mrcp_profile->server_ip,
                mrcp_profile->server_port, mrcp_profile->mrcp_version);
        }
    }

    switch_xml_free(xml);

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Create MRCP client
 */
static switch_status_t create_mrcp_client(void)
{
    mrcp_client_t *client;
    apt_dir_layout_t *dir_layout;
    const char *root_dir_path;
    const char *log_conf_path;

    /* Get FreeSWITCH directory paths */
    root_dir_path = switch_core_get_variable("base_dir");
    if (!root_dir_path) {
        root_dir_path = SWITCH_GLOBAL_dirs.base_dir;
    }

    /* Create directory layout */
    dir_layout = apt_default_dir_layout_create(root_dir_path, mod_pool);
    if (!dir_layout) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, "Failed to create directory layout\n");
        return SWITCH_STATUS_GENERR;
    }

    /* Initialize UniMRCP client logging */
    apt_log_instance_create(APT_LOG_OUTPUT_CONSOLE | APT_LOG_OUTPUT_FILE,
                            APT_PRIO_INFO, mod_pool);

    /* Set log file path */
    log_conf_path = apt_dir_layout_path_compose(dir_layout, APT_LAYOUT_LOG_DIR,
                                                 "unimrcp_client.log", mod_pool);
    apt_log_file_open(log_conf_path, MAX_LOG_FILE_SIZE, MAX_LOG_FILE_COUNT,
                      FALSE, mod_pool);

    /* Create the MRCP client instance */
    client = unimrcp_client_create(dir_layout);
    if (!client) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, "Failed to create UniMRCP client\n");
        return SWITCH_STATUS_GENERR;
    }

    globals.mrcp_client = client;

    /* Start the MRCP client */
    if (mrcp_client_start(client) != TRUE) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR, "Failed to start UniMRCP client\n");
        mrcp_client_destroy(client);
        globals.mrcp_client = NULL;
        return SWITCH_STATUS_GENERR;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_INFO, "UniMRCP client started successfully\n");

    return SWITCH_STATUS_SUCCESS;
}

/*
 * Destroy MRCP client
 */
static void destroy_mrcp_client(void)
{
    if (globals.mrcp_client) {
        mrcp_client_shutdown(globals.mrcp_client);
        mrcp_client_destroy(globals.mrcp_client);
        globals.mrcp_client = NULL;
    }

    apt_log_instance_destroy();
}

/*
 * Get module globals
 */
mod_unimrcp_globals_t *unimrcp_get_globals(void)
{
    return &globals;
}

/*
 * Get MRCP profile by name
 */
mrcp_profile_t *unimrcp_get_profile(const char *name)
{
    if (!name || !globals.profiles) {
        return NULL;
    }
    return switch_core_hash_find(globals.profiles, name);
}

/*
 * API command handler
 */
SWITCH_STANDARD_API(unimrcp_api_function)
{
    char *mycmd = NULL, *argv[10] = { 0 };
    int argc = 0;

    if (zstr(cmd)) {
        stream->write_function(stream, "-USAGE: %s\n", UNIMRCP_API_SYNTAX);
        return SWITCH_STATUS_SUCCESS;
    }

    mycmd = strdup(cmd);
    argc = switch_separate_string(mycmd, ' ', argv, (sizeof(argv) / sizeof(argv[0])));

    if (argc < 1) {
        stream->write_function(stream, "-USAGE: %s\n", UNIMRCP_API_SYNTAX);
        goto done;
    }

    if (!strcasecmp(argv[0], "status")) {
        stream->write_function(stream, "UniMRCP Module Status:\n");
        stream->write_function(stream, "  Client: %s\n",
            globals.mrcp_client ? "Running" : "Stopped");
        stream->write_function(stream, "  Default ASR Profile: %s\n", globals.default_asr_profile);
        stream->write_function(stream, "  Default TTS Profile: %s\n", globals.default_tts_profile);
        stream->write_function(stream, "  Max Connections: %d\n", globals.max_connection_count);
        stream->write_function(stream, "  Request Timeout: %d ms\n", globals.request_timeout);
    } else if (!strcasecmp(argv[0], "profile")) {
        if (argc < 2) {
            switch_hash_index_t *hi;
            void *val;
            const void *key;

            stream->write_function(stream, "Available MRCP Profiles:\n");
            if (globals.profiles) {
                for (hi = switch_core_hash_first(globals.profiles); hi;
                     hi = switch_core_hash_next(&hi)) {
                    switch_core_hash_this(hi, &key, NULL, &val);
                    mrcp_profile_t *p = (mrcp_profile_t *)val;
                    stream->write_function(stream, "  %s: %s:%d (MRCPv%d)\n",
                        p->name, p->server_ip, p->server_port, p->mrcp_version);
                }
            }
        } else {
            mrcp_profile_t *p = unimrcp_get_profile(argv[1]);
            if (p) {
                stream->write_function(stream, "Profile: %s\n", p->name);
                stream->write_function(stream, "  Server: %s:%d\n", p->server_ip, p->server_port);
                stream->write_function(stream, "  Client: %s:%d\n", p->client_ip, p->client_port);
                stream->write_function(stream, "  MRCP Version: %d\n", p->mrcp_version);
                stream->write_function(stream, "  Codec: %s @ %dHz\n", p->codec, p->sample_rate);
                if (p->rtp_ip) {
                    stream->write_function(stream, "  RTP: %s (%d-%d)\n",
                        p->rtp_ip, p->rtp_port_min, p->rtp_port_max);
                }
            } else {
                stream->write_function(stream, "Profile '%s' not found\n", argv[1]);
            }
        }
    } else if (!strcasecmp(argv[0], "reload")) {
        stream->write_function(stream, "Reloading configuration...\n");
        if (do_config() == SWITCH_STATUS_SUCCESS) {
            stream->write_function(stream, "+OK Configuration reloaded\n");
        } else {
            stream->write_function(stream, "-ERR Failed to reload configuration\n");
        }
    } else {
        stream->write_function(stream, "-USAGE: %s\n", UNIMRCP_API_SYNTAX);
    }

done:
    switch_safe_free(mycmd);
    return SWITCH_STATUS_SUCCESS;
}

/* ASR Application Message Handlers */
static apt_bool_t asr_on_session_update(mrcp_application_t *application,
                                         mrcp_session_t *session,
                                         mrcp_sig_status_code_e status)
{
    speech_channel_t *schannel = mrcp_application_session_object_get(session);
    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR session update: channel=%s, status=%d\n",
        schannel ? schannel->name : "unknown", status);
    return TRUE;
}

static apt_bool_t asr_on_session_terminate(mrcp_application_t *application,
                                            mrcp_session_t *session,
                                            mrcp_sig_status_code_e status)
{
    speech_channel_t *schannel = mrcp_application_session_object_get(session);
    if (schannel) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
            "ASR session terminated: channel=%s\n", schannel->name);
        speech_channel_set_state(schannel, SPEECH_CHANNEL_CLOSED);
    }
    return TRUE;
}

static apt_bool_t asr_on_channel_add(mrcp_application_t *application,
                                      mrcp_session_t *session,
                                      mrcp_channel_t *channel,
                                      mrcp_sig_status_code_e status)
{
    speech_channel_t *schannel = mrcp_application_session_object_get(session);
    if (schannel) {
        if (status == MRCP_SIG_STATUS_CODE_SUCCESS) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                "ASR channel added: channel=%s\n", schannel->name);
            schannel->unimrcp_channel = channel;
            speech_channel_set_state(schannel, SPEECH_CHANNEL_READY);
        } else {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "ASR channel add failed: channel=%s, status=%d\n",
                schannel->name, status);
            speech_channel_set_state(schannel, SPEECH_CHANNEL_ERROR);
        }
    }
    return TRUE;
}

static apt_bool_t asr_on_channel_remove(mrcp_application_t *application,
                                         mrcp_session_t *session,
                                         mrcp_channel_t *channel,
                                         mrcp_sig_status_code_e status)
{
    speech_channel_t *schannel = mrcp_application_session_object_get(session);
    if (schannel) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
            "ASR channel removed: channel=%s\n", schannel->name);
        schannel->unimrcp_channel = NULL;
    }
    mrcp_application_session_terminate(session);
    return TRUE;
}

static apt_bool_t asr_on_message_receive(mrcp_application_t *application,
                                          mrcp_session_t *session,
                                          mrcp_channel_t *channel,
                                          mrcp_message_t *message)
{
    speech_channel_t *schannel = mrcp_application_session_object_get(session);
    if (!schannel) {
        return FALSE;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "ASR message received: channel=%s, method=%d\n",
        schannel->name, message->start_line.method_id);

    if (message->start_line.message_type == MRCP_MESSAGE_TYPE_RESPONSE) {
        /* Handle MRCP response */
        if (message->start_line.method_id == RECOGNIZER_RECOGNIZE) {
            if (message->start_line.request_state == MRCP_REQUEST_STATE_INPROGRESS) {
                switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                    "Recognition started: channel=%s\n", schannel->name);
                speech_channel_set_state(schannel, SPEECH_CHANNEL_PROCESSING);
            } else if (message->start_line.status_code != MRCP_STATUS_CODE_SUCCESS) {
                switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                    "Recognition failed: channel=%s, status=%d\n",
                    schannel->name, message->start_line.status_code);
                speech_channel_set_state(schannel, SPEECH_CHANNEL_ERROR);
            }
        } else if (message->start_line.method_id == RECOGNIZER_STOP) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                "Recognition stopped: channel=%s\n", schannel->name);
            speech_channel_set_state(schannel, SPEECH_CHANNEL_READY);
        }
    } else if (message->start_line.message_type == MRCP_MESSAGE_TYPE_EVENT) {
        /* Handle MRCP event */
        if (message->start_line.method_id == RECOGNIZER_RECOGNITION_COMPLETE) {
            mrcp_recog_header_t *recog_header;
            recog_header = (mrcp_recog_header_t *)mrcp_resource_header_get(message);

            if (recog_header && mrcp_resource_header_property_check(message,
                    RECOGNIZER_HEADER_COMPLETION_CAUSE)) {
                switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                    "Recognition complete: channel=%s, cause=%d\n",
                    schannel->name, recog_header->completion_cause);

                if (recog_header->completion_cause == RECOGNIZER_COMPLETION_CAUSE_SUCCESS) {
                    /* Get recognition result */
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
                "Start of input: channel=%s\n", schannel->name);
        }
    }

    return TRUE;
}

/* TTS Application Message Handlers */
static apt_bool_t tts_on_session_update(mrcp_application_t *application,
                                         mrcp_session_t *session,
                                         mrcp_sig_status_code_e status)
{
    speech_channel_t *schannel = mrcp_application_session_object_get(session);
    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "TTS session update: channel=%s, status=%d\n",
        schannel ? schannel->name : "unknown", status);
    return TRUE;
}

static apt_bool_t tts_on_session_terminate(mrcp_application_t *application,
                                            mrcp_session_t *session,
                                            mrcp_sig_status_code_e status)
{
    speech_channel_t *schannel = mrcp_application_session_object_get(session);
    if (schannel) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
            "TTS session terminated: channel=%s\n", schannel->name);
        speech_channel_set_state(schannel, SPEECH_CHANNEL_CLOSED);
    }
    return TRUE;
}

static apt_bool_t tts_on_channel_add(mrcp_application_t *application,
                                      mrcp_session_t *session,
                                      mrcp_channel_t *channel,
                                      mrcp_sig_status_code_e status)
{
    speech_channel_t *schannel = mrcp_application_session_object_get(session);
    if (schannel) {
        if (status == MRCP_SIG_STATUS_CODE_SUCCESS) {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                "TTS channel added: channel=%s\n", schannel->name);
            schannel->unimrcp_channel = channel;
            speech_channel_set_state(schannel, SPEECH_CHANNEL_READY);
        } else {
            switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_ERROR,
                "TTS channel add failed: channel=%s, status=%d\n",
                schannel->name, status);
            speech_channel_set_state(schannel, SPEECH_CHANNEL_ERROR);
        }
    }
    return TRUE;
}

static apt_bool_t tts_on_channel_remove(mrcp_application_t *application,
                                         mrcp_session_t *session,
                                         mrcp_channel_t *channel,
                                         mrcp_sig_status_code_e status)
{
    speech_channel_t *schannel = mrcp_application_session_object_get(session);
    if (schannel) {
        switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
            "TTS channel removed: channel=%s\n", schannel->name);
        schannel->unimrcp_channel = NULL;
    }
    mrcp_application_session_terminate(session);
    return TRUE;
}

static apt_bool_t tts_on_message_receive(mrcp_application_t *application,
                                          mrcp_session_t *session,
                                          mrcp_channel_t *channel,
                                          mrcp_message_t *message)
{
    speech_channel_t *schannel = mrcp_application_session_object_get(session);
    if (!schannel) {
        return FALSE;
    }

    switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
        "TTS message received: channel=%s, method=%d\n",
        schannel->name, message->start_line.method_id);

    if (message->start_line.message_type == MRCP_MESSAGE_TYPE_RESPONSE) {
        /* Handle MRCP response */
        if (message->start_line.method_id == SYNTHESIZER_SPEAK) {
            if (message->start_line.request_state == MRCP_REQUEST_STATE_INPROGRESS) {
                switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                    "Synthesis started: channel=%s\n", schannel->name);
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
        /* Handle MRCP event */
        if (message->start_line.method_id == SYNTHESIZER_SPEAK_COMPLETE) {
            mrcp_synth_header_t *synth_header;
            synth_header = (mrcp_synth_header_t *)mrcp_resource_header_get(message);

            if (synth_header && mrcp_resource_header_property_check(message,
                    SYNTHESIZER_HEADER_COMPLETION_CAUSE)) {
                switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_DEBUG,
                    "Synthesis complete: channel=%s, cause=%d\n",
                    schannel->name, synth_header->completion_cause);
            }
            speech_channel_set_state(schannel, SPEECH_CHANNEL_DONE);
        }
    }

    return TRUE;
}

/*
 * Speech channel state management
 */
void speech_channel_set_state(speech_channel_t *schannel, speech_channel_state_t state)
{
    if (!schannel) {
        return;
    }

    switch_mutex_lock(schannel->mutex);
    schannel->state = state;
    switch_thread_cond_signal(schannel->cond);
    switch_mutex_unlock(schannel->mutex);
}

speech_channel_state_t speech_channel_get_state(speech_channel_t *schannel)
{
    speech_channel_state_t state;

    if (!schannel) {
        return SPEECH_CHANNEL_CLOSED;
    }

    switch_mutex_lock(schannel->mutex);
    state = schannel->state;
    switch_mutex_unlock(schannel->mutex);

    return state;
}

switch_status_t speech_channel_wait_for_state(speech_channel_t *schannel,
                                               speech_channel_state_t state,
                                               uint32_t timeout_ms)
{
    switch_status_t status = SWITCH_STATUS_SUCCESS;
    switch_time_t timeout;

    if (!schannel) {
        return SWITCH_STATUS_FALSE;
    }

    timeout = switch_micro_time_now() + (timeout_ms * 1000);

    switch_mutex_lock(schannel->mutex);
    while (schannel->state != state && schannel->state != SPEECH_CHANNEL_ERROR &&
           schannel->state != SPEECH_CHANNEL_CLOSED) {
        switch_status_t cond_status;
        cond_status = switch_thread_cond_timedwait(schannel->cond, schannel->mutex,
                                                    timeout_ms * 1000);
        if (cond_status == SWITCH_STATUS_TIMEOUT ||
            switch_micro_time_now() >= timeout) {
            status = SWITCH_STATUS_TIMEOUT;
            break;
        }
    }
    switch_mutex_unlock(schannel->mutex);

    return status;
}

/* EOF */
