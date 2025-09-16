import os
from sqlalchemy import create_engine, Column, String, DateTime, Float, Text, Integer
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from datetime import datetime
import asyncio

# Database configuration
DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://freeswitch:freeswitch123@localhost:5432/freeswitch_calls")

# Create async engine
engine = create_async_engine(DATABASE_URL.replace("postgresql://", "postgresql+asyncpg://"))
AsyncSessionLocal = sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

Base = declarative_base()

class CallSession(Base):
    __tablename__ = "call_sessions"
    
    id = Column(Integer, primary_key=True, index=True)
    call_id = Column(String, unique=True, index=True)
    phone_number = Column(String, index=True)
    agent_id = Column(String, index=True)
    status = Column(String)  # initiating, active, ended, failed
    start_time = Column(DateTime)
    end_time = Column(DateTime)
    created_at = Column(DateTime, default=datetime.now)

class Transcription(Base):
    __tablename__ = "transcriptions"
    
    id = Column(Integer, primary_key=True, index=True)
    call_id = Column(String, index=True)
    text = Column(Text)
    confidence = Column(Float)
    timestamp = Column(DateTime)
    created_at = Column(DateTime, default=datetime.now)

async def get_db():
    """Get database session"""
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()

async def init_db():
    """Initialize database tables"""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)