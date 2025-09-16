from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, delete
from datetime import datetime
from typing import List, Optional
from .database import CallSession as CallSessionDB, Transcription as TranscriptionDB, AsyncSessionLocal

class CallSession:
    def __init__(self, call_id: str, phone_number: str, agent_id: str, 
                 status: str = "initiating", start_time: datetime = None):
        self.call_id = call_id
        self.phone_number = phone_number
        self.agent_id = agent_id
        self.status = status
        self.start_time = start_time or datetime.now()
        self.end_time = None

    async def save(self):
        """Save call session to database"""
        async with AsyncSessionLocal() as session:
            db_session = CallSessionDB(
                call_id=self.call_id,
                phone_number=self.phone_number,
                agent_id=self.agent_id,
                status=self.status,
                start_time=self.start_time,
                end_time=self.end_time
            )
            session.add(db_session)
            await session.commit()

    @staticmethod
    async def get_by_call_id(call_id: str) -> Optional['CallSession']:
        """Get call session by call_id"""
        async with AsyncSessionLocal() as session:
            result = await session.execute(
                select(CallSessionDB).where(CallSessionDB.call_id == call_id)
            )
            db_session = result.scalar_one_or_none()
            if db_session:
                return CallSession(
                    call_id=db_session.call_id,
                    phone_number=db_session.phone_number,
                    agent_id=db_session.agent_id,
                    status=db_session.status,
                    start_time=db_session.start_time
                )
            return None

    @staticmethod
    async def get_all() -> List['CallSession']:
        """Get all call sessions"""
        async with AsyncSessionLocal() as session:
            result = await session.execute(select(CallSessionDB))
            db_sessions = result.scalars().all()
            return [
                CallSession(
                    call_id=s.call_id,
                    phone_number=s.phone_number,
                    agent_id=s.agent_id,
                    status=s.status,
                    start_time=s.start_time
                )
                for s in db_sessions
            ]

class Transcription:
    def __init__(self, call_id: str, text: str, confidence: float = 0.9, 
                 timestamp: datetime = None):
        self.call_id = call_id
        self.text = text
        self.confidence = confidence
        self.timestamp = timestamp or datetime.now()

    async def save(self):
        """Save transcription to database"""
        async with AsyncSessionLocal() as session:
            db_transcription = TranscriptionDB(
                call_id=self.call_id,
                text=self.text,
                confidence=self.confidence,
                timestamp=self.timestamp
            )
            session.add(db_transcription)
            await session.commit()

    @staticmethod
    async def get_by_call_id(call_id: str) -> List['Transcription']:
        """Get all transcriptions for a call"""
        async with AsyncSessionLocal() as session:
            result = await session.execute(
                select(TranscriptionDB)
                .where(TranscriptionDB.call_id == call_id)
                .order_by(TranscriptionDB.timestamp)
            )
            db_transcriptions = result.scalars().all()
            return [
                Transcription(
                    call_id=t.call_id,
                    text=t.text,
                    confidence=t.confidence,
                    timestamp=t.timestamp
                )
                for t in db_transcriptions
            ]