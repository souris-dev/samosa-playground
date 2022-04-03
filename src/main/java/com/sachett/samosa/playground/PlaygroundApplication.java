package com.sachett.samosa.playground;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.FileCopyUtils;

import java.io.*;

@SpringBootApplication
public class PlaygroundApplication {
	// TODO: make these config vars
	private static final String inJarCompilerJarPath = "/binaries/samosac-1.0-SNAPSHOT-full.jar";
	private static final String fsCompilerJarPath = "./compiler/samosac-1.0-SNAPSHOT-full.jar";
	private static final String fsCompilerJarDir = "./compiler";

	public static void main(String[] args) {
		// First extract the compiler to the right path
		if (System.getenv("ON_BEANSTALK") != null) {
			// running on beanstalk

			// First check if we already have the compilerJar copied to the file system
			File compilerFile = new File(fsCompilerJarPath);

			// if not, then copy it from within the fat jar to the filesystem outside
			if (!compilerFile.exists()) {
				try {
					new File(fsCompilerJarDir).mkdirs();
					compilerFile.createNewFile();

					InputStream compilerJarStream = PlaygroundApplication.class.getResourceAsStream(inJarCompilerJarPath);
					OutputStream compilerJarOut = new BufferedOutputStream(new FileOutputStream(fsCompilerJarPath));

					if (compilerJarStream == null) {
						throw new Exception("Compiler JAR resource not found in fat JAR.");
					}

					FileCopyUtils.copy(compilerJarStream, compilerJarOut);
					compilerJarStream.close();
					compilerJarOut.close();
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}
		}
		SpringApplication.run(PlaygroundApplication.class, args);
	}

}
