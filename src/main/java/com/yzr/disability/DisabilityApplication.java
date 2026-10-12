package com.yzr.disability;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yzr.disability.mapper")
public class DisabilityApplication {

	public static void main(String[] args) {
		SpringApplication.run(DisabilityApplication.class, args);
	}

}
