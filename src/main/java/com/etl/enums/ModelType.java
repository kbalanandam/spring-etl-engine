package com.etl.enums;

public enum ModelType {
	
	SOURCE("source"), TARGET("target"), PROCESSOR("processor");

	private String type;

	ModelType(String type) {
		this.type = type;
	}

	public String getType() {
		return type;
	}

	public static ModelType fromString(String type) {
		for (ModelType modelType : ModelType.values()) {
			if (modelType.type.equalsIgnoreCase(type)) {
				return modelType;
			}
		}
		throw new IllegalArgumentException("Unknown model type: " + type);
	}

}
