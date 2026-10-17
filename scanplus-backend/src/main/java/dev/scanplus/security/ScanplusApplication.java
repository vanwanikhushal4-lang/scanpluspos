package dev.scanplus.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
public class ScanplusApplication {
    public static void main(String[] args) { SpringApplication.run(ScanplusApplication.class,args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
