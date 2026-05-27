package com.etl.processor.spi;

import com.etl.processor.transform.PartnerStatusTranslateProcessorTransform;
import com.etl.processor.transform.ProcessorFieldTransform;

import java.util.List;

/**
 * Showcase provider demonstrating ServiceLoader-based custom transform extension.
 */
public class ShowcaseProcessorExtensionProvider implements ProcessorExtensionProvider {

	@Override
	public String providerId() {
		return "showcase-partner-transform";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public List<ProcessorFieldTransform> transforms() {
		return List.of(new PartnerStatusTranslateProcessorTransform());
	}
}

