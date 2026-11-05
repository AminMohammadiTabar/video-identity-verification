package com.example.videokyc.dms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class DmsClockConfiguration {
    @Bean
    Clock utcClock() { return Clock.systemUTC(); }
}
