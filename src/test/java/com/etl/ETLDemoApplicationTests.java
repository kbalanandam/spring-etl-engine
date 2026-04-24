package com.etl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"etl.config.allow-demo-fallback=true",
		"etl.logging.base-dir=target/test-logs"
})
class ETLDemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
