import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetapostService {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public byte[] renderMetapostToPng(Path metapostInputFile) throws IOException {
		if (!Files.exists(metapostInputFile)) {
			throw new FileNotFoundException("Can't find file: " + metapostInputFile);
		}
		Path tempFile = Files.copy(metapostInputFile, Files.createTempFile(null, ".mp"), StandardCopyOption.REPLACE_EXISTING);
		String tempFileBaseName = FilenameUtils.getBaseName(tempFile.getFileName().toString());
		Path workingDir = tempFile.getParent();
		Path compilerOutputFile = workingDir.resolve(tempFileBaseName + ".1");
		Path epsFile = workingDir.resolve(tempFileBaseName + ".eps");

		Files.deleteIfExists(compilerOutputFile);
		Files.deleteIfExists(epsFile);

		try {
			String[] metapostCommand = { "mpost", "-interaction=nonstopmode", tempFile.getFileName().toString(), "end" };
			Process metapostProcess = Runtime.getRuntime().exec(metapostCommand, null, workingDir.toFile());
			metapostProcess.waitFor();
			logProcessOutput(metapostProcess, metapostCommand);
			if (metapostProcess.exitValue() != 0) {
				throw new IOException("Compilation failed. Metapost returned code " + metapostProcess.exitValue());
			} else if (!Files.exists(compilerOutputFile)) {
				throw new IOException("Output file " + compilerOutputFile + " is missing");
			}
			Files.move(compilerOutputFile, epsFile, StandardCopyOption.REPLACE_EXISTING);
			Path pngFile = convertEpsToPng(epsFile);
			return Files.readAllBytes(pngFile);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private Path convertEpsToPng(Path epsFile) throws IOException, InterruptedException {
		String targetFileName = FilenameUtils.getBaseName(epsFile.getFileName().toString()) + ".png";
		Path workingDirectory = epsFile.getParent();
		Path outputFile = workingDirectory.resolve(targetFileName);
		Files.deleteIfExists(outputFile);

		String[] convertCommand = {"convert", epsFile.getFileName().toString(), targetFileName};
		Process convertProcess = Runtime.getRuntime().exec(convertCommand, null, workingDirectory.toFile());
		convertProcess.waitFor();
		logProcessOutput(convertProcess, convertCommand);

		// ghostscript
		if (!Files.exists(outputFile)) {
			String[] ghostscriptCommand = getGhostscriptCommand(epsFile.getFileName().toString(), targetFileName);
			Process ghostscriptProcess = Runtime.getRuntime().exec(ghostscriptCommand, null, workingDirectory.toFile());
			ghostscriptProcess.waitFor();
			logProcessOutput(ghostscriptProcess, ghostscriptCommand);
		}
		if (!Files.exists(outputFile)) {
			throw new IOException("Unable to convert eps to png.");
		}
		return outputFile;
	}

	private String[] getGhostscriptCommand(String input, String output) {
		String binary = System.getProperty("os.name").toLowerCase().indexOf("win") != -1 ? "gsWin32" : "gs";
		return new String[] { binary, "-dBATCH", "-dNOPAUSE", "-dQUIET", "-dEPSCrop", "-dEPSFitPage", "-dGraphicsAlphaBits=4", "-dTextAlphaBits=4", "-sDEVICE=pngalpha", "-sOutputFile=" + output, input };
	}

	private static void logProcessOutput(Process process, String[] args) {
		if (process.exitValue() == 0 && !logger.isDebugEnabled()) {
			return;
		}

		String command = String.join(" ", args);
		StringBuilder sb = new StringBuilder("Exit code: [").append(process.exitValue()).append("], command: [").append(command).append("]");
		try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append('\n').append(line);
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		sb.append("\nExit code: ").append(process.exitValue());

		if (process.exitValue() != 0) {
			logger.error(sb.toString());
		} else {
			logger.debug(sb.toString());
		}
	}
}
