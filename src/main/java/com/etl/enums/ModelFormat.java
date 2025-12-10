package com.etl.enums;

// Model format can be csv, json, xml, realational table etc.
public enum ModelFormat {
	CSV("csv"), JSON("json"), XML("xml"), RELATIONAL("relational");

	private String format;

	ModelFormat(String format) {
		this.format = format;
	}

	public String getFormat() {
		return format;
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
