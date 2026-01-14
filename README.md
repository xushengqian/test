# IVR System

A modular IVR (Interactive Voice Response) system built with Spring Boot and Vue.js.

## Project Structure

- `ivr-backend/`: Java Spring Boot application handling core logic and APIs.
- `ivr-frontend/`: Vue 3 application for management dashboard and flow configuration.

## Prerequisites

- Java 17+
- Node.js 18+
- Maven 3.8+

## Getting Started

### Backend

```bash
cd ivr-backend
# Run with Maven
./mvnw spring-boot:run
# Or if you have maven installed
mvn spring-boot:run
```

The backend will start on http://localhost:8080.

### Frontend

```bash
cd ivr-frontend
npm install
npm run dev
```

The frontend will start on http://localhost:5173 (default Vite port).

## Features

- **Call Flow Control**: Manage voice interactions.
- **Web Dashboard**: Visual interface for monitoring and configuration.
