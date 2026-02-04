package scala.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import scala.parser.lsp.LanguageServerLauncher;

@SpringBootApplication
public class ParserApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(ParserApplication.class, args);

		LanguageServerLauncher.start();
	}

}
