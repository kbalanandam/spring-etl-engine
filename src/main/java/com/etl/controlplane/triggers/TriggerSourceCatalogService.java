package com.etl.controlplane.triggers;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Provides trigger-source options for API/UI filters from the master table when available.
 */
@Service
public class TriggerSourceCatalogService implements TriggerSourceCatalog {

	private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

	public TriggerSourceCatalogService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
		this.jdbcTemplateProvider = jdbcTemplateProvider;
	}

	@Override
	public List<TriggerSourceOptionView> listActiveSources() {
		JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
		if (jdbcTemplate == null) {
			return TriggerSourceCatalog.defaultSources();
		}
		try {
			return jdbcTemplate.query("""
					select source_code, display_name, description
					from controlplane_trigger_source
					where is_active = 1
					order by trigger_source_pk asc
					""", (rs, rowNum) -> new TriggerSourceOptionView(
					rs.getString("source_code"),
					rs.getString("display_name"),
					rs.getString("description")
			));
		} catch (DataAccessException ignored) {
			return TriggerSourceCatalog.defaultSources();
		}
	}
}

