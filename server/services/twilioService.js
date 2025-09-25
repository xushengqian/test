const twilio = require('twilio');

class TwilioService {
  constructor() {
    this.client = null;
    this.phoneNumber = process.env.TWILIO_PHONE_NUMBER;
    this.initialize();
  }

  initialize() {
    if (process.env.TWILIO_ACCOUNT_SID && process.env.TWILIO_AUTH_TOKEN) {
      this.client = twilio(
        process.env.TWILIO_ACCOUNT_SID,
        process.env.TWILIO_AUTH_TOKEN
      );
      console.log('Twilio service initialized');
    } else {
      console.warn('Twilio credentials not configured');
    }
  }

  async makeCall(toNumber, callId) {
    if (!this.client) {
      throw new Error('Twilio client not initialized');
    }

    try {
      const call = await this.client.calls.create({
        to: toNumber,
        from: this.phoneNumber,
        url: `${process.env.BASE_URL}/api/calls/twiml/${callId}`,
        statusCallback: `${process.env.BASE_URL}/api/calls/status/${callId}`,
        statusCallbackEvent: ['initiated', 'ringing', 'answered', 'completed'],
        record: true,
        recordingStatusCallback: `${process.env.BASE_URL}/api/calls/recording/${callId}`,
        machineDetection: 'Enable',
        machineDetectionTimeout: 3000
      });

      console.log(`Call initiated: ${call.sid} to ${toNumber}`);
      return call;
    } catch (error) {
      console.error('Failed to make call:', error);
      throw error;
    }
  }

  async endCall(callSid) {
    if (!this.client) {
      throw new Error('Twilio client not initialized');
    }

    try {
      await this.client.calls(callSid).update({
        status: 'completed'
      });
      console.log(`Call ended: ${callSid}`);
    } catch (error) {
      console.error('Failed to end call:', error);
      throw error;
    }
  }

  async sayInCall(callSid, message) {
    if (!this.client) {
      throw new Error('Twilio client not initialized');
    }

    try {
      // Create TwiML for saying the message
      const twiml = new twilio.twiml.VoiceResponse();
      twiml.say({
        voice: 'alice',
        language: 'zh-CN'
      }, message);

      // Update the call with new TwiML
      await this.client.calls(callSid).update({
        twiml: twiml.toString()
      });
      
      console.log(`Message sent in call ${callSid}: ${message}`);
    } catch (error) {
      console.error('Failed to say in call:', error);
      throw error;
    }
  }

  async bridgeAgentToCall(callSid, agentId) {
    if (!this.client) {
      throw new Error('Twilio client not initialized');
    }

    try {
      // Create a conference for the call
      const conferenceName = `call-${callSid}`;
      
      // Update the original call to join conference
      const twiml = new twilio.twiml.VoiceResponse();
      twiml.dial().conference({
        startConferenceOnEnter: true,
        endConferenceOnExit: false,
        waitUrl: 'http://twimlets.com/holdmusic?Bucket=com.twilio.music.classical',
        record: 'record-from-start',
        recordingStatusCallback: `${process.env.BASE_URL}/api/calls/conference-recording/${callSid}`
      }, conferenceName);

      await this.client.calls(callSid).update({
        twiml: twiml.toString()
      });

      // Call the agent and add them to the conference
      const agentCall = await this.client.calls.create({
        to: this.getAgentPhone(agentId), // You need to implement this
        from: this.phoneNumber,
        twiml: `<Response><Dial><Conference>${conferenceName}</Conference></Dial></Response>`
      });

      console.log(`Agent ${agentId} bridged to call ${callSid}`);
      return agentCall;
    } catch (error) {
      console.error('Failed to bridge agent to call:', error);
      throw error;
    }
  }

  getAgentPhone(agentId) {
    // This should look up the agent's phone number from database
    // For demo, using a placeholder
    return process.env[`AGENT_${agentId}_PHONE`] || process.env.DEFAULT_AGENT_PHONE;
  }

  async getCallRecording(callSid) {
    if (!this.client) {
      throw new Error('Twilio client not initialized');
    }

    try {
      const recordings = await this.client.recordings.list({
        callSid: callSid,
        limit: 20
      });

      return recordings;
    } catch (error) {
      console.error('Failed to get recordings:', error);
      throw error;
    }
  }

  async createMediaStream(callSid) {
    if (!this.client) {
      throw new Error('Twilio client not initialized');
    }

    try {
      // Create a media stream for real-time audio
      const stream = await this.client.media.v1.mediaStreams.create({
        url: `wss://${process.env.BASE_URL}/api/calls/stream/${callSid}`
      });

      return stream;
    } catch (error) {
      console.error('Failed to create media stream:', error);
      throw error;
    }
  }
}

module.exports = new TwilioService();