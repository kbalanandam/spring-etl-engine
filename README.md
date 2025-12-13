# Spring ETL Engine

A lightweight, configurable, and modular ETL (Extract–Transform–Load) framework built using **Spring Boot**, designed for handling multiple source formats and multiple target destinations dynamically. The engine supports CSV/XML ingestion, dynamic field mappings, reflection-based transformations, type conversion utilities, profile‑based configuration, and structured logging.

## Features
- **Dynamic Mapping Framework**: Map any source structure to any target using configs.
- **Reusable Utilities**: Centralized `*Utils` for type conversion, reflection, validation.
- **Custom Exceptions**: Domain‑specific exceptions to identify issues clearly.
- **AOP Logging**: Automatic method-level logging for ETL flow visibility.
- **Builder Pattern**: Clean and safe object construction.
- **Profile-based Execution**: Separate dev, test, prod behaviour.
- **Multi‑Source Input**: Currently CSV/XML; extendable.
- **Multi‑Target Output**: Extendable (e.g., MySQL Writer, File Writer, API Writer).

## Repository Structure
```
/spring-etl-engine
 ├── src/main/java
 │    ├── config/          # Profile configs, dynamic mapping configs
 │    ├── mapper/          # FieldSetMapper, DynamicMapper
 │    ├── utils/           # ReflectionUtils, TypeConversionUtils, FileUtils
 │    ├── writer/          # Target writers (CSV Writer, MySQL Writer, etc.)
 │    ├── exception/       # Custom exception classes
 │    ├── aop/             # Logging AOP
 │    └── service/         # Core ETL service
 ├── src/main/resources
 │    ├── mapping/         # Mapping JSON/YAML files
 │    └── application.yml
 ├── README.md
 └── LICENSE
```

## Installation
Clone the repository:
```
git clone https://github.com/kbalanandam/spring-etl-engine.git
```

Run the project:
```
mvn spring-boot:run
```

## Usage
1. Place your CSV/XML files in the configured input directory.
2. Define mapping configuration in `/resources/mapping/`.
3. Enable desired writer (e.g., MySQL Writer) via profile.
4. The ETL engine automatically loads, maps, transforms, and writes data.

## Example Mapping
```yaml
source: customer.csv
fields:
  - source: id
    target: customerId
  - source: first_name
    target: firstName
```

## License
This project is licensed under the **MIT License**.

![License](https://img.shields.io/badge/License-MIT-green.svg)

See the full license in the `LICENSE` file.

