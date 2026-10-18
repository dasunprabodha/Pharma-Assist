package lk.sliit.prescription;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // ImageStore.purgeExpired
public class PrescriptionReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrescriptionReaderApplication.class, args);
    }
}
