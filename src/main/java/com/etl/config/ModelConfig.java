package com.etl.config;

import com.etl.enums.ModelType;

public interface ModelConfig {
    String getModelName();
    ModelType getModelType();
   // String getModelFormat();
}
