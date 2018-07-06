package it.bancaditalia.oss;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.Rengine;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

public abstract class AbstractRMojo extends AbstractMojo
{
	private static Rengine													engine;

	@Component protected MavenProjectHelper									mavenProjectHelper;
	@Parameter(defaultValue = "${project}") protected MavenProject			project;
	@Parameter(defaultValue = "${session}") protected MavenSession			session;

	/**
	 * Location of R installation directory. Default value is taken from R_HOME environment variable.
	 */
	@Parameter(defaultValue = "${env.R_HOME}", property = "rHome") File		rHome;

	/**
	 * Additional paths where to locate shared libraries needed by R and R packages.
	 */
	@Parameter(property = "sharedLibs") File[]								sharedLibs;

	/**
	 * True if the artifact produced by the build should be attached to the project.
	 */
	@Parameter(defaultValue = "true", property = "attachArtifact") boolean	attachArtifact;

	/**
	 * Classifier of produced R artifact.
	 */
	@Parameter(property = "classifier") String								classifier;

	private interface libc extends Library
	{
		libc INSTANCE = (libc) Native.loadLibrary(Platform.isWindows() ? "msvcrt" : "c", libc.class);

		int setenv(String name, String value, int overwrite);
	}

	public synchronized Rengine getEngine() throws MojoExecutionException
	{
		if (engine == null)
			try
			{
				if (rHome == null || !rHome.exists() || !rHome.isDirectory())
					throw new MojoExecutionException(
							"Environment variable R_HOME is not set or invalid. Either set it or use <rHome> property in configuration.");

				File libjri = new File(rHome, "library/rJava/jri/libjri.so");
				if (!libjri.exists() && !libjri.isFile())
					throw new MojoExecutionException("Library libjri.so cannot be found. Ensure that rJava package is installed in R.");

				System.setProperty("java.library.path",
						System.getProperty("java.library.path") + File.pathSeparator + libjri.getParentFile().getAbsolutePath());

				// set LD_LIBRARY_PATH (for *NIX)
				StringBuilder builder = new StringBuilder();
				builder.append(libjri.getParentFile().getAbsolutePath()).append(File.pathSeparator);
				if (sharedLibs != null)
					for (File sharedLib : sharedLibs)
						builder.append(sharedLib.getAbsolutePath()).append(File.pathSeparator);
				builder.append(System.getenv("LD_LIBRARY_PATH"));
				libc.INSTANCE.setenv("LD_LIBRARY_PATH", builder.toString(), 1);

				// set PATH (for Win)
				builder.setLength(0);
				builder.append(libjri.getParentFile().getAbsolutePath()).append(File.pathSeparator);
				if (sharedLibs != null)
					for (File sharedLib : sharedLibs)
						builder.append(sharedLib.getAbsolutePath()).append(File.pathSeparator);
				builder.append(System.getenv("PATH"));
				libc.INSTANCE.setenv("PATH", builder.toString(), 1);

				// set java.library.path
				builder.setLength(0);
				builder.append(libjri.getParentFile().getAbsolutePath()).append(File.pathSeparator);
				if (sharedLibs != null)
					for (File sharedLib : sharedLibs)
						builder.append(sharedLib.getAbsolutePath()).append(File.pathSeparator);
				builder.append(System.getProperty("java.library.path"));
				System.setProperty("java.library.path", builder.toString());

				final Field sysPathsField = ClassLoader.class.getDeclaredField("sys_paths");
				sysPathsField.setAccessible(true);
				sysPathsField.set(null, null);

				
				try
				{
					System.loadLibrary("jri");
				} 
				catch (UnsatisfiedLinkError e)
				{
					if (!e.getMessage().endsWith("already loaded in another classloader"))
						throw e;
				}
				libc.INSTANCE.setenv("R_HOME", rHome.getAbsolutePath(), 1);

				getLog().info("");
				getLog().info("Starting R engine...");

				engine = new Rengine(new String[] { "--vanilla" }, false, null);
				getLog().info("Querying available R packages...");
				REXP packages = engine.eval("installed.packages()[,'Package']");
				if (packages == null || packages.asStringArray() == null)
					throw new MojoExecutionException("Cannot query R for installed packages.");
				getLog().debug("Available R packages: " + Arrays.toString(packages.asStringArray()));
				boolean found = false;
				for (String packName : packages.asStringArray())
					if ("devtools".equals(packName))
					{
						getLog().debug("Package 'devtools' found.");
						found = true;
						break;
					}

				if (!found)
					throw new MojoExecutionException("Package devtools is not installed in R.");
				else
				{
					getLog().info("Loading required packages...");
					REXP res = tryCatch("library(devtools)");
					if (res == null)
						throw new MojoExecutionException("Unexpected error while loading R. Please check R runtime requirements and try again");
				}
			}
			catch (MojoExecutionException | RuntimeException | Error e)
			{
				throw e;
			}
			catch (Exception e)
			{
				throw new MojoExecutionException("Unexpected error", e);
			}

		return engine;
	}

	public REXP tryCatch(String expression) throws MojoExecutionException
	{
		synchronized (engine)
		{
			expression = "tryCatch({ " + expression + "}, error = function(e) { e <- e[1,1]; attr(e, 'ERROR') <- T; e })";
			REXP res = engine.eval(expression);
			if (res != null && res.getAttribute("ERROR") != null && res.getAttribute("ERROR").asBool() != null && res.getAttribute("ERROR").asBool().isTRUE())
				throw new MojoExecutionException("R engine threw an error: " + res);
			else
				return res;
		}
	}
	
	protected String checkRPackageVersion() throws MojoExecutionException
	{
		Matcher versionMatcher = Pattern.compile("(\\d+[-.]\\d+([-.]\\d+)?).*").matcher(project.getVersion());
		if (versionMatcher.find())
			return versionMatcher.group(1);
		else
			throw new MojoExecutionException(
					"Project version \"" + project.getVersion() + "\" does not match regular expression \"(\\d+[-.]\\d+([-.]\\d+)?).*\"");
	}
	
	protected void setupDirectories()
	{
		new File(project.getBuild().getDirectory()).mkdirs();
		new File(project.getBuild().getOutputDirectory()).mkdirs();
	}
}
