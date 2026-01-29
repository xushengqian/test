/*
 * mod_unimrcp.c - FreeSWITCH module skeleton for UniMRCP integration.
 *
 * This is a minimal, loadable module which registers:
 * - an application: unimrcp (dialplan)
 * - an API: unimrcp_status (fscli)
 *
 * Real MRCP session/profile/media logic should be implemented on top of this
 * skeleton by integrating UniMRCP Client SDK.
 */

#include <switch.h>

SWITCH_MODULE_LOAD_FUNCTION(mod_unimrcp_load);
SWITCH_MODULE_SHUTDOWN_FUNCTION(mod_unimrcp_shutdown);
SWITCH_MODULE_DEFINITION(mod_unimrcp, mod_unimrcp_load, mod_unimrcp_shutdown, NULL);

static const char *UNIMRCP_APP_NAME = "unimrcp";
static const char *UNIMRCP_API_NAME = "unimrcp_status";

static switch_status_t unimrcp_app_function(switch_core_session_t *session, const char *data)
{
	switch_channel_t *channel = switch_core_session_get_channel(session);
	const char *uuid = switch_core_session_get_uuid(session);

	switch_log_printf(SWITCH_CHANNEL_SESSION_LOG(session), SWITCH_LOG_INFO,
		"mod_unimrcp app invoked. uuid=%s data=%s\n",
		uuid ? uuid : "(null)",
		data ? data : "(null)");

	/*
	 * Placeholder behavior:
	 * - Set a channel variable so dialplan can assert module execution.
	 */
	switch_channel_set_variable(channel, "unimrcp_last_app_data", data ? data : "");
	switch_channel_set_variable(channel, "unimrcp_last_app_status", "OK");

	return SWITCH_STATUS_SUCCESS;
}

SWITCH_STANDARD_API(unimrcp_status_function)
{
	stream->write_function(stream,
		"mod_unimrcp: loaded (skeleton)\n"
		"app: %s\n"
		"api: %s\n",
		UNIMRCP_APP_NAME,
		UNIMRCP_API_NAME);
	return SWITCH_STATUS_SUCCESS;
}

SWITCH_MODULE_LOAD_FUNCTION(mod_unimrcp_load)
{
	switch_application_interface_t *app_interface = NULL;
	switch_api_interface_t *api_interface = NULL;

	*module_interface = switch_loadable_module_create_module_interface(pool, modname);

	SWITCH_ADD_APP(app_interface,
		UNIMRCP_APP_NAME,
		"UniMRCP integration (skeleton)",
		"Placeholder application to be extended with UniMRCP ASR/TTS logic",
		unimrcp_app_function,
		"[options]",
		SAF_SUPPORT_NOMEDIA);

	SWITCH_ADD_API(api_interface,
		UNIMRCP_API_NAME,
		"Show UniMRCP module status",
		unimrcp_status_function,
		"");

	switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_NOTICE,
		"mod_unimrcp loaded (skeleton)\n");

	return SWITCH_STATUS_SUCCESS;
}

SWITCH_MODULE_SHUTDOWN_FUNCTION(mod_unimrcp_shutdown)
{
	switch_log_printf(SWITCH_CHANNEL_LOG, SWITCH_LOG_NOTICE,
		"mod_unimrcp shutdown\n");
	return SWITCH_STATUS_SUCCESS;
}

