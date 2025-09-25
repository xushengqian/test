import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { useSocket } from './SocketContext';

interface Call {
  id: string;
  customerId: string;
  phoneNumber: string;
  status: string;
  isRobotHandling: boolean;
  agentId: string | null;
  startTime: Date;
  duration: number;
  transcript: TranscriptEntry[];
}

interface TranscriptEntry {
  speaker: 'robot' | 'customer' | 'agent' | 'system';
  text: string;
  timestamp: Date;
}

interface CallContextType {
  activeCalls: Call[];
  currentCall: Call | null;
  callQueue: Call[];
  needsAgentCalls: Call[];
  joinCall: (callId: string) => void;
  leaveCall: () => void;
  interveneCall: (callId: string) => void;
  takeoverCall: (callId: string) => void;
  releaseCall: (callId: string) => void;
  sendMessage: (message: string) => void;
}

const CallContext = createContext<CallContextType | undefined>(undefined);

export const useCall = () => {
  const context = useContext(CallContext);
  if (!context) {
    throw new Error('useCall must be used within a CallProvider');
  }
  return context;
};

interface CallProviderProps {
  children: ReactNode;
}

export const CallProvider: React.FC<CallProviderProps> = ({ children }) => {
  const [activeCalls, setActiveCalls] = useState<Call[]>([]);
  const [currentCall, setCurrentCall] = useState<Call | null>(null);
  const [callQueue, setCallQueue] = useState<Call[]>([]);
  const [needsAgentCalls, setNeedsAgentCalls] = useState<Call[]>([]);
  const { socket } = useSocket();

  useEffect(() => {
    if (!socket) return;

    // Listen for call events
    socket.on('call:new', (call: Call) => {
      setActiveCalls(prev => [...prev, call]);
      setCallQueue(prev => [...prev, call]);
    });

    socket.on('queue:update', (queue: Call[]) => {
      setCallQueue(queue);
      setActiveCalls(queue);
    });

    socket.on('call:needs_agent', (data: any) => {
      const call = activeCalls.find(c => c.id === data.callId);
      if (call) {
        setNeedsAgentCalls(prev => [...prev, call]);
      }
    });

    socket.on('call:joined', (call: Call) => {
      setCurrentCall(call);
    });

    socket.on('call:transcript', (data: any) => {
      if (currentCall && currentCall.id === data.callId) {
        setCurrentCall(prev => {
          if (!prev) return null;
          return {
            ...prev,
            transcript: [...prev.transcript, {
              speaker: data.speaker,
              text: data.text,
              timestamp: data.timestamp
            }]
          };
        });
      }

      setActiveCalls(prev => prev.map(call => {
        if (call.id === data.callId) {
          return {
            ...call,
            transcript: [...call.transcript, {
              speaker: data.speaker,
              text: data.text,
              timestamp: data.timestamp
            }]
          };
        }
        return call;
      }));
    });

    socket.on('call:status_update', (data: any) => {
      setActiveCalls(prev => prev.map(call => {
        if (call.id === data.callId) {
          return { ...call, status: data.status };
        }
        return call;
      }));

      if (currentCall && currentCall.id === data.callId) {
        setCurrentCall(prev => prev ? { ...prev, status: data.status } : null);
      }
    });

    socket.on('call:agent_intervened', (data: any) => {
      setActiveCalls(prev => prev.map(call => {
        if (call.id === data.callId) {
          return { ...call, agentId: data.agentId };
        }
        return call;
      }));
    });

    socket.on('call:agent_takeover', (data: any) => {
      setActiveCalls(prev => prev.map(call => {
        if (call.id === data.callId) {
          return { ...call, agentId: data.agentId, isRobotHandling: false };
        }
        return call;
      }));
    });

    socket.on('call:released_to_robot', (data: any) => {
      setActiveCalls(prev => prev.map(call => {
        if (call.id === data.callId) {
          return { ...call, agentId: null, isRobotHandling: true };
        }
        return call;
      }));
    });

    socket.on('call:ended', (data: any) => {
      setActiveCalls(prev => prev.filter(call => call.id !== data.callId));
      setCallQueue(prev => prev.filter(call => call.id !== data.callId));
      setNeedsAgentCalls(prev => prev.filter(call => call.id !== data.callId));
      
      if (currentCall && currentCall.id === data.callId) {
        setCurrentCall(null);
      }
    });

    return () => {
      socket.off('call:new');
      socket.off('queue:update');
      socket.off('call:needs_agent');
      socket.off('call:joined');
      socket.off('call:transcript');
      socket.off('call:status_update');
      socket.off('call:agent_intervened');
      socket.off('call:agent_takeover');
      socket.off('call:released_to_robot');
      socket.off('call:ended');
    };
  }, [socket, currentCall, activeCalls]);

  const joinCall = (callId: string) => {
    const call = activeCalls.find(c => c.id === callId);
    if (call) {
      setCurrentCall(call);
    }
  };

  const leaveCall = () => {
    setCurrentCall(null);
  };

  const interveneCall = (callId: string) => {
    if (!socket) return;
    
    socket.emit('agent:intervene', {
      callId,
      agentId: localStorage.getItem('agentId')
    });
  };

  const takeoverCall = (callId: string) => {
    if (!socket) return;
    
    socket.emit('agent:takeover', {
      callId,
      agentId: localStorage.getItem('agentId')
    });
  };

  const releaseCall = (callId: string) => {
    if (!socket) return;
    
    socket.emit('agent:release', {
      callId,
      agentId: localStorage.getItem('agentId')
    });
  };

  const sendMessage = (message: string) => {
    if (!socket || !currentCall) return;
    
    socket.emit('agent:message', {
      callId: currentCall.id,
      agentId: localStorage.getItem('agentId'),
      message
    });
  };

  const value = {
    activeCalls,
    currentCall,
    callQueue,
    needsAgentCalls,
    joinCall,
    leaveCall,
    interveneCall,
    takeoverCall,
    releaseCall,
    sendMessage
  };

  return <CallContext.Provider value={value}>{children}</CallContext.Provider>;
};