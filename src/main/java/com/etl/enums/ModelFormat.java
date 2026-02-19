package com.etl.enums;

import lombok.Getter;

// Model format can be csv, json, xml, realational table etc.
@Getter
public enum ModelFormat {
	CSV("csv"), JSON("json"), XML("xml"), RELATIONAL("relational");

	private final String format;

	ModelFormat(String format) {
		this.format = format;
	}

    public static ModelFormat fromString(String format) {
		for (ModelFormat fmt : ModelFormat.values()) {
			if (fmt.format.equalsIgnoreCase(format)) {
				return fmt;
			}
		}
		throw new IllegalArgumentException("Unknown model format: " + format);
	}

}
