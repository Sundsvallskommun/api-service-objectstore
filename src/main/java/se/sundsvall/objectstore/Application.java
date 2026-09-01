package se.sundsvall.objectstore;

import se.sundsvall.dept44.ServiceApplication;
import se.sundsvall.dept44.util.jacoco.ExcludeFromJacocoGeneratedCoverageReport;

import static org.springframework.boot.SpringApplication.run;

@ServiceApplication
@ExcludeFromJacocoGeneratedCoverageReport
public class Application {
	public static void main(String[] args) {
		run(Application.class, args);
	}
}
