package com.etl.config.target;

import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.etl.enums.ModelType;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.Getter;

import java.util.List;

@Getter
@JsonTypeInfo(
		use = JsonTypeInfo.Id.NAME,
		include = JsonTypeInfo.As.PROPERTY,
		property = "format" // <-- discriminator in YAML
)
@JsonSubTypes({
		@JsonSubTypes.Type(value = CsvTargetConfig.class, name = "csv"),
		@JsonSubTypes.Type(value = XmlTargetConfig.class, name = "xml")
		// future: db, kafka, etc.
})
public abstract class TargetConfig implements ModelConfig {

	private final String targetName;
	private final String packageName;
	private final List<? extends FieldDefinition> fields;

	protected TargetConfig(
			String targetName,
			String packageName,
			List<? extends FieldDefinition> fields
	) {
		this.targetName = targetName;
		this.packageName = packageName;
		this.fields = fields;
	}

    /** csv, xml, mysql, etc */
	public abstract ModelFormat getFormat();

	@Override
	public String getModelName() {
		return this.getTargetName();
	}

	@Override
	public ModelType getModelType() {
		return ModelType.TARGET;
	}
}