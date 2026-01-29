/*
 * mod_unimrcp.h - FreeSWITCH UniMRCP Module Header
 *
 * Copyright (c) 2026
 * Licensed under the Apache License 2.0
 */

#ifndef MOD_UNIMRCP_H
#define MOD_UNIMRCP_H

#include <switch.h>
#include <apr_general.h>
#include <apr_pools.h>

#include "mrcp_client.h"
#include "mrcp_application.h"
#include "mrcp_channel.h"

/* Constants */
#define MAX_LOG_FILE_SIZE (10 * 1024 * 1024)  /* 10 MB */
#define MAX_LOG_FILE_COUNT 10

#define DEFAULT_ASR_TIMEOUT 30000   /* 30 seconds */
#define DEFAULT_TTS_TIMEOUT 30000   /* 30 seconds */

#define UNIMRCP_API_SYNTAX "unimrcp [status|profile [name]|reload]"

/* Speech channel states */
typedef enum {
    SPEECH_CHANNEL_CLOSED = 0,
    SPEECH_CHANNEL_READY,
    SPEECH_CHANNEL_PROCESSING,
    SPEECH_CHANNEL_DONE,
    SPEECH_CHANNEL_ERROR
} speech_channel_state_t;

/* Speech channel types */
typedef enum {
    SPEECH_CHANNEL_ASR = 0,
    SPEECH_CHANNEL_TTS
} speech_channel_type_t;

/* MRCP profile configuration */
typedef struct {
    const char *name;
    const char *server_ip;
    int server_port;
    const char *client_ip;
    int client_port;
    int mrcp_version;           /* 1 or 2 */
    const char *codec;
    int sample_rate;
    const char *sip_transport;  /* udp, tcp, tls */
    const char *rtp_ip;
    int rtp_port_min;
    int rtp_port_max;
} mrcp_profile_t;

/* Speech channel structure */
typedef struct speech_channel_s {
    char *name;                      /* Channel name/identifier */
    speech_channel_type_t type;      /* ASR or TTS */
    speech_channel_state_t state;    /* Current state */
    switch_memory_pool_t *pool;      /* Memory pool */
    switch_mutex_t *mutex;           /* Mutex for state synchronization */
    switch_thread_cond_t *cond;      /* Condition variable for state waits */

    /* MRCP resources */
    mrcp_application_t *application;
    mrcp_session_t *session;
    mrcp_channel_t *unimrcp_channel;
    mrcp_profile_t *profile;

    /* Audio buffer for TTS */
    switch_buffer_t *audio_buffer;
    switch_codec_t read_codec;
    switch_codec_t write_codec;

    /* ASR specific */
    char *grammar;
    char *result;
    int start_input_timers;

    /* TTS specific */
    char *voice_name;
    char *voice_language;
    float speech_rate;
    float volume;

    /* Parameters */
    switch_hash_t *params;
} speech_channel_t;

/* Module globals */
typedef struct {
    switch_memory_pool_t *pool;
    switch_mutex_t *mutex;
    mrcp_client_t *mrcp_client;
    switch_hash_t *profiles;

    /* Default settings */
    const char *default_asr_profile;
    const char *default_tts_profile;
    switch_log_level_t log_level;
    int max_connection_count;
    int offer_new_connection;
    int request_timeout;
} mod_unimrcp_globals_t;

/* Function prototypes - Module core */
mod_unimrcp_globals_t *unimrcp_get_globals(void);
mrcp_profile_t *unimrcp_get_profile(const char *name);

/* Function prototypes - Speech channel management */
void speech_channel_set_state(speech_channel_t *schannel, speech_channel_state_t state);
speech_channel_state_t speech_channel_get_state(speech_channel_t *schannel);
switch_status_t speech_channel_wait_for_state(speech_channel_t *schannel,
                                               speech_channel_state_t state,
                                               uint32_t timeout_ms);

/* Function prototypes - ASR interface */
switch_status_t unimrcp_asr_open(switch_asr_handle_t *ah,
                                  const char *codec,
                                  int rate,
                                  const char *dest,
                                  switch_asr_flag_t *flags);
switch_status_t unimrcp_asr_close(switch_asr_handle_t *ah,
                                   switch_asr_flag_t *flags);
switch_status_t unimrcp_asr_load_grammar(switch_asr_handle_t *ah,
                                          const char *grammar,
                                          const char *name);
switch_status_t unimrcp_asr_unload_grammar(switch_asr_handle_t *ah,
                                            const char *name);
switch_status_t unimrcp_asr_feed(switch_asr_handle_t *ah,
                                  void *data,
                                  unsigned int len,
                                  switch_asr_flag_t *flags);
switch_status_t unimrcp_asr_resume(switch_asr_handle_t *ah);
switch_status_t unimrcp_asr_pause(switch_asr_handle_t *ah);
switch_status_t unimrcp_asr_check_results(switch_asr_handle_t *ah,
                                           switch_asr_flag_t *flags);
switch_status_t unimrcp_asr_get_results(switch_asr_handle_t *ah,
                                         char **xmlstr,
                                         switch_asr_flag_t *flags);
switch_status_t unimrcp_asr_start_input_timers(switch_asr_handle_t *ah);
void unimrcp_asr_text_param(switch_asr_handle_t *ah, char *param, const char *val);
void unimrcp_asr_numeric_param(switch_asr_handle_t *ah, char *param, int val);
void unimrcp_asr_float_param(switch_asr_handle_t *ah, char *param, double val);

/* Function prototypes - TTS interface */
switch_status_t unimrcp_speech_open(switch_speech_handle_t *sh,
                                     const char *voice_name,
                                     int rate,
                                     int channels,
                                     switch_speech_flag_t *flags);
switch_status_t unimrcp_speech_close(switch_speech_handle_t *sh,
                                      switch_speech_flag_t *flags);
switch_status_t unimrcp_speech_feed_tts(switch_speech_handle_t *sh,
                                         char *text,
                                         switch_speech_flag_t *flags);
switch_status_t unimrcp_speech_read_tts(switch_speech_handle_t *sh,
                                         void *data,
                                         switch_size_t *datalen,
                                         switch_speech_flag_t *flags);
void unimrcp_speech_flush_tts(switch_speech_handle_t *sh);
void unimrcp_speech_text_param_tts(switch_speech_handle_t *sh, char *param, const char *val);
void unimrcp_speech_numeric_param_tts(switch_speech_handle_t *sh, char *param, int val);
void unimrcp_speech_float_param_tts(switch_speech_handle_t *sh, char *param, double val);

/* Function prototypes - API */
SWITCH_STANDARD_API(unimrcp_api_function);

#endif /* MOD_UNIMRCP_H */
