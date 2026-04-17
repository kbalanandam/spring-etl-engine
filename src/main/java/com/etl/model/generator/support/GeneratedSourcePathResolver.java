package com.etl.model.generator.support;

import com.etl.config.ModelPathConfig;
import com.etl.enums.ModelType;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves generated source output paths for model generators.
 */
public final class GeneratedSourcePathResolver {

	private GeneratedSourcePathResolver() {
	}

	public static Path resolveSourceRoot(ModelPathConfig modelPathConfig,
										 ModelType modelType,
										 String packageName) {
		Path configuredDirectory = Paths.get(
				modelType == ModelType.SOURCE
						? modelPathConfig.getSourceDir()
						: modelPathConfig.getTargetDir())
				.normalize();

		Path packagePath = Paths.get("", packageName.split("\\."));
		if (!configuredDirectory.endsWith(packagePath)) {
			return configuredDirectory;
		}

		Path sourceRoot = configuredDirectory;
		for (int i = 0; i < packagePath.getNameCount(); i++) {
			sourceRoot = sourceRoot.getParent();
		}
		return sourceRoot == null ? configuredDirectory : sourceRoot.normalize();
	}

	public static Path resolvePackageDirectory(ModelPathConfig modelPathConfig,
											  ModelType modelType,
											  String packageName) {
		Path configuredDirectory = resolveSourceRoot(modelPathConfig, modelType, packageName);
		Path packagePath = Paths.get("", packageName.split("\\."));
		return configuredDirectory.endsWith(packagePath)
				? configuredDirectory
				: configuredDirectory.resolve(packagePath).normalize();
	}

	public static Path resolveJavaFile(ModelPathConfig modelPathConfig,
									 ModelType modelType,
									 String packageName,
									 String className) {
		return resolvePackageDirectory(modelPathConfig, modelType, packageName)
				.resolve(className + ".java");
	}
}

