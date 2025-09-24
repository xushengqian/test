from setuptools import setup, find_packages

setup(
    name="enterprise-metric-scheduler",
    version="1.0.0",
    description="Enterprise Metric Scheduling System with Database Pressure Management",
    author="Your Name",
    packages=find_packages(),
    install_requires=[
        "sqlalchemy>=2.0.23",
        "redis>=5.0.1",
        "celery>=5.3.4",
        "apscheduler>=3.10.4",
        "psutil>=5.9.6",
        "prometheus-client>=0.19.0",
        "pydantic>=2.5.2",
        "pymysql>=1.1.0",
        "psycopg2-binary>=2.9.9",
        "aioredis>=2.0.1",
        "aiomysql>=0.2.0",
        "asyncpg>=0.29.0",
        "python-dotenv>=1.0.0",
        "loguru>=0.7.2",
        "tenacity>=8.2.3"
    ],
    python_requires=">=3.8",
)