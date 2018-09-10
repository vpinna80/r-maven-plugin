package it.bancaditalia.oss;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.apache.maven.shared.utils.StringUtils;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.ReaderFactory;

/**
 * <p>Goal that prepares R sources. Standard location for R package sources is inside src/main/R.</p>
 * 
 * <p>This class uses code extracted from <a href="https://maven.apache.org/plugins/maven-resources-plugin/">Maven Resources Plugin</a>.</p>
 * 
 * <p>Maven Resources Plugin is licensed under the <a href="http://www.apache.org/licenses/">Apache License</a>.</p>
 */
@Mojo(name = "sources", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class RSourcesMojo extends AbstractRMojo
{
	/**
	 * The character encoding scheme to be applied when filtering R sources.
	 */
	@Parameter(defaultValue = "${project.build.sourceEncoding}") protected String							encoding;

	/**
	 * The output directory into which to copy the R sources.
	 */
	@Parameter(defaultValue = "${project.build.outputDirectory}", required = true) private File				outputDirectory;

	/**
	 * Set it to {@code false} to disable automatic filtering of R source files.
	 */
	@Parameter(defaultValue = "true") protected boolean													filterSources;

	/**
	 * The list of additional filter properties files to be used along with System and project properties, which would be
	 * used for the filtering.
	 * 
	 * @see RSourcesMojo#filters
	 *
	 * @since 2.4
	 */
	@Parameter(defaultValue = "${project.build.filters}", readonly = true) protected List<String>			buildFilters;

	/**
	 * The list of extra filter properties files to be used along with System properties, project properties, and filter
	 * properties files specified in the POM build/filters section, which should be used for the filtering during the
	 * current mojo execution.
	 */
	@Parameter protected List<String>																		filters;

	/**
	 * If false, don't use the filters specified in the build/filters section of the POM when processing sources in this
	 * mojo execution.
	 * 
	 * @see RSourcesMojo#buildFilters
	 * @see RSourcesMojo#filters
	 *
	 * @since 2.4
	 */
	@Parameter(defaultValue = "true") protected boolean														useBuildFilters;

	/**
	 *
	 */
	@Component(role = MavenResourcesFiltering.class, hint = "default") protected MavenResourcesFiltering	mavenResourcesFiltering;

	/**
	 * Expressions preceded with this string won't be interpolated. Anything else preceded with this string will be passed
	 * through unchanged. For example {@code \${foo}} will be replaced with {@code ${foo}} but {@code \\${foo}} will be
	 * replaced with {@code \\value of foo}, if this parameter has been set to the backslash.
	 * 
	 * @since 2.3
	 */
	@Parameter protected String																				escapeString;

	/**
	 * Whether to escape backslashes and colons in windows-style paths.
	 *
	 * @since 2.4
	 */
	@Parameter(defaultValue = "true") protected boolean														escapeWindowsPaths;

	/**
	 * Additional file extensions to not apply filtering (already defined are : jpg, jpeg, gif, bmp, png)
	 *
	 * @since 2.3
	 */
	@Parameter protected List<String>																		nonFilteredFileExtensions;

	/**
	 * <p>
	 * Set of delimiters for expressions to filter within the sources. These delimiters are specified in the form
	 * {@code beginToken*endToken}. If no {@code *} is given, the delimiter is assumed to be the same for start and end.
	 * </p>
	 * <p>
	 * So, the default filtering delimiters might be specified as:
	 * </p>
	 * 
	 * <pre>
	 * &lt;delimiters&gt;
	 *   &lt;delimiter&gt;${*}&lt;/delimiter&gt;
	 *   &lt;delimiter&gt;@&lt;/delimiter&gt;
	 * &lt;/delimiters&gt;
	 * </pre>
	 * <p>
	 * Since the {@code @} delimiter is the same on both ends, we don't need to specify {@code @*@} (though we can).
	 * </p>
	 *
	 * @since 2.4
	 */
	@Parameter protected LinkedHashSet<String>																delimiters;

	/**
	 * Use default delimiters in addition to custom delimiters, if any.
	 *
	 * @since 2.4
	 */
	@Parameter(defaultValue = "true") protected boolean														useDefaultDelimiters;

	/**
	 * <p>
	 * List of plexus components hint which implements
	 * {@link MavenResourcesFiltering#filterResources(MavenResourcesExecution)}. They will be executed after the sources
	 * copying/filtering.
	 * </p>
	 *
	 * @since 2.4
	 */
	@Parameter private List<String>																			mavenFilteringHints;

	/**
	 * @since 2.4
	 */
	private PlexusContainer																					plexusContainer;

	/**
	 * @since 2.4
	 */
	private List<MavenResourcesFiltering>																	mavenFilteringComponents	= new ArrayList<>();

	/**
	 * stop searching endToken at the end of line
	 *
	 * @since 2.5
	 */
	@Parameter(defaultValue = "false") private boolean														supportMultiLineFiltering;

	/**
	 * Support filtering of filenames folders etc.
	 * 
	 * @since 3.0.0
	 */
	@Parameter(defaultValue = "false") private boolean														fileNameFiltering;

	/**
	 * You can skip the execution of the plugin if you need to. Its use is NOT RECOMMENDED, but quite convenient on
	 * occasion.
	 * 
	 * @since 3.0.0
	 */
	@Parameter(property = "R.sources.skip", defaultValue = "false") private boolean							skip;

	@Override
	public void execute() throws MojoExecutionException
	{
		Log log = getLog();

		if (isSkip())
		{
			getLog().info("Skipping the execution.");
			return;
		}

		log.info("Copying R sources...");

		Resource sourceResourceDirectory = new Resource();
		String sourceDirectory = project.getBuild().getSourceDirectory();

		if (sourceDirectory == null || sourceDirectory.matches("(.*/)?src/main/java"))
			sourceResourceDirectory.setDirectory(new File(project.getBasedir(), "src/main/R").getAbsolutePath());
		else
			sourceResourceDirectory.setDirectory(sourceDirectory);
		sourceResourceDirectory.setFiltering(filterSources);

		List<Resource> resources = Collections.singletonList(sourceResourceDirectory);

		if (StringUtils.isEmpty(encoding))
		{
			getLog().warn("File encoding has not been set, using platform encoding " + ReaderFactory.FILE_ENCODING + ", i.e. build is platform dependent!");
			getLog().warn("Please take a look into the FAQ: https://maven.apache.org/general.html#encoding-warning");
			
			encoding = Charset.defaultCharset().toString();
		}

		try
		{
			MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(resources, getOutputDirectory(), project, encoding,
					getCombinedFiltersList(), Collections.<String>emptyList(), session);

			mavenResourcesExecution.setEscapeWindowsPaths(escapeWindowsPaths);

			// never include project build filters in this call, since we've already accounted for the POM build filters
			// above, in getCombinedFiltersList().
			mavenResourcesExecution.setInjectProjectBuildFilters(false);

			mavenResourcesExecution.setEscapeString(escapeString);
			mavenResourcesExecution.setOverwrite(true);
			mavenResourcesExecution.setIncludeEmptyDirs(true);
			mavenResourcesExecution.setSupportMultiLineFiltering(supportMultiLineFiltering);
			mavenResourcesExecution.setFilterFilenames(fileNameFiltering);
			mavenResourcesExecution.setAddDefaultExcludes(false);

			// if these are NOT set, just use the defaults, which are '${*}' and '@'.
			mavenResourcesExecution.setDelimiters(delimiters, useDefaultDelimiters);

			if (nonFilteredFileExtensions != null)
			{
				mavenResourcesExecution.setNonFilteredFileExtensions(nonFilteredFileExtensions);
			}
			mavenResourcesFiltering.filterResources(mavenResourcesExecution);

			executeUserFilterComponents(mavenResourcesExecution);

			File description = new File(outputDirectory, "DESCRIPTION");
			if (description.canRead())
			{
				if (description.length() > 100000)
					throw new MojoExecutionException("DESCRIPTION file is too big.");

				// This will patch DESCRIPTION file to use pom values.
				List<String> lines = new LinkedList<>();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(description), encoding)))
				{
					String line;

					while ((line = reader.readLine()) != null)
					{
						if (line.startsWith("Version:"))
							line = "Version: " + checkRPackageVersion();
						else if (line.startsWith("License:"))
						{
							line = "License:";
							for (License license: project.getLicenses())
								line += " " + license.getName();
						}
						else if (line.startsWith("Author:"))
						{
							line = "Author:";
							for (Developer dev: project.getDevelopers())
								line += " " + dev.getName();
						}
						else if (line.startsWith("Maintainer:") && project.getDevelopers().size() > 0)
							line = "Maintainer: " + project.getDevelopers().get(0).getName() + " <" + project.getDevelopers().get(0).getEmail() + ">";

						lines.add(line);
					}
				}

				try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(description), encoding)))
				{
					// Fix Windows paths
					for (String line : lines)
						writer.println(line.replaceAll("\\\\", "\\\\"));
				}
			}
		}
		catch (MavenFilteringException | IOException e)
		{
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	/**
	 * @param mavenResourcesExecution {@link MavenResourcesExecution}
	 * @throws MojoExecutionException in case of wrong lookup.
	 * @throws MavenFilteringException in case of failure.
	 * @since 2.5
	 */
	protected void executeUserFilterComponents(MavenResourcesExecution mavenResourcesExecution) throws MojoExecutionException, MavenFilteringException
	{

		if (mavenFilteringHints != null)
		{
			for (String hint : mavenFilteringHints)
			{
				try
				{
					// CHECKSTYLE_OFF: LineLength
					mavenFilteringComponents.add((MavenResourcesFiltering) plexusContainer.lookup(MavenResourcesFiltering.class.getName(), hint));
					// CHECKSTYLE_ON: LineLength
				}
				catch (ComponentLookupException e)
				{
					throw new MojoExecutionException(e.getMessage(), e);
				}
			}
		}
		else
		{
			getLog().debug("no use filter components");
		}

		if (mavenFilteringComponents != null && !mavenFilteringComponents.isEmpty())
		{
			getLog().debug("execute user filters");
			for (MavenResourcesFiltering filter : mavenFilteringComponents)
			{
				filter.filterResources(mavenResourcesExecution);
			}
		}
	}

	/**
	 * @return The combined filters.
	 */
	protected List<String> getCombinedFiltersList()
	{
		if (filters == null || filters.isEmpty())
		{
			return useBuildFilters ? buildFilters : null;
		}
		else
		{
			List<String> result = new ArrayList<>();

			if (useBuildFilters && buildFilters != null && !buildFilters.isEmpty())
			{
				result.addAll(buildFilters);
			}

			result.addAll(filters);

			return result;
		}
	}

	/**
	 * @return {@link #outputDirectory}
	 */
	public File getOutputDirectory()
	{
		return outputDirectory;
	}

	/**
	 * @param outputDirectory the output folder.
	 */
	public void setOutputDirectory(File outputDirectory)
	{
		this.outputDirectory = outputDirectory;
	}

	/**
	 * @return {@link #filters}
	 */
	public List<String> getFilters()
	{
		return filters;
	}

	/**
	 * @param filters The filters to use.
	 */
	public void setFilters(List<String> filters)
	{
		this.filters = filters;
	}

	/**
	 * @return {@link #delimiters}
	 */
	public LinkedHashSet<String> getDelimiters()
	{
		return delimiters;
	}

	/**
	 * @param delimiters The delimiters to use.
	 */
	public void setDelimiters(LinkedHashSet<String> delimiters)
	{
		this.delimiters = delimiters;
	}

	/**
	 * @return {@link #useDefaultDelimiters}
	 */
	public boolean isUseDefaultDelimiters()
	{
		return useDefaultDelimiters;
	}

	/**
	 * @param useDefaultDelimiters true to use {@code ${*}}
	 */
	public void setUseDefaultDelimiters(boolean useDefaultDelimiters)
	{
		this.useDefaultDelimiters = useDefaultDelimiters;
	}

	/**
	 * @return {@link #skip}
	 */
	public boolean isSkip()
	{
		return skip;
	}

}
