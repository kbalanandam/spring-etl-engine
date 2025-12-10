# Spring ETL Engine

A lightweight, configurable ETL (Extract–Transform–Load) engine built using **Spring Batch**. The goal is to create an ETL system that is fully dynamic — meaning **no hard‑coded readers, writers, processors, or POJO types**. Everything is configuration-driven.

This makes the engine easy to extend, reusable across projects, and ideal for teams that need to onboard new file formats or database targets without changing Java code.

---

## ⭐ Features

### ✔ Dynamic Readers, Processors, and Writers
- Readers, processors, and writers are selected at runtime using a **type-based factory**.
- Supports multiple source and target types.
- Adding support for a new type requires implementing **one interface**.

### ✔ Dynamic Mapping Engine
- Transforms source objects into target objects using mapping rules defined in configuration.
- Supports nested fields (`address.street → streetName`).
- Uses shared reflection + type conversion utilities.

### ✔ Shared Utility Layer
- `TypeConversionUtils` – converts values safely.
- `ReflectionUtils` – read/write fields dynamically.
- `MappingUtils` – apply field-to-field mapping.
- Includes custom exceptions for better debugging.

### ✔ Multi-Step ETL Job
- Each Source–Target pair becomes an independent Spring Batch **Step**.
- All steps run inside a single ETL job.

### ✔ AOP Logging
- Automatic method logging, execution time tracking.

### ✔ Spring Profiles
- Separate configurations for `dev`, `test`, `prod` environments.

---

## 📁 Directory Structure (Simplified)
```
src/main/java/com/etl
├── common
│   ├── exception/
│   ├── util/
├── reader/
├── writer/
├── processor/
├── config/
├── aspect/
```

---

## 🚀 How It Works

### 1️⃣ Load ETL Configuration
The engine reads configuration (YAML/JSON) that defines:
- Source type
- Target type
- Column mapping rules
- Processor type

### 2️⃣ Build Dynamic Steps
Spring Batch creates one step per source–target pair:
```
reader → processor → writer
```

### 3️⃣ Execute the Job
All steps run sequentially (or can be parallelized).

---

## 📝 Example Mapping
```yaml
fields:
  - from: id
    to: customerId
  - from: name
    to: fullName
```

---

## ▶ Running the Project
```
mvn spring-boot:run
```
Or run from IntelliJ.

---

## 📦 Packaging
```
mvn clean package
```
Runs ETL job inside a Spring Boot executable jar.

---

## 📘 Documentation
- `CHANGELOG.md` – version history
- `README.md` – project overview

---

## 🤝 Contributing
Feel free to submit PRs or suggest improvements.

---

## 📜 License
MIT License.

