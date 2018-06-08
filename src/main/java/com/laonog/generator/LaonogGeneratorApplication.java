package com.laonog.generator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.laonog.generator.mapper")
public class LaonogGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(LaonogGeneratorApplication.class, args);
	}
}
