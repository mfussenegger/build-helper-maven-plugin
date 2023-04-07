package org.codehaus.mojo.buildhelper;

/*
 * The MIT License
 *
 * Copyright (c) 2004, The Codehaus
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import java.util.*;
import java.util.stream.Collectors;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.*;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.repository.legacy.metadata.DefaultMetadataResolutionRequest;
import org.apache.maven.repository.legacy.metadata.MetadataResolutionRequest;

/**
 * Resolve the latest released version of this project. This mojo sets the following properties:
 *
 * <pre>
 *   [propertyPrefix].version
 *   [propertyPrefix].majorVersion
 *   [propertyPrefix].minorVersion
 *   [propertyPrefix].incrementalVersion
 * </pre>
 *
 * Where the propertyPrefix is the string set in the mojo parameter.
 *
 * @author Robert Scholte
 * @since 1.6
 */
@Mojo( name = "released-version", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true )
public class ReleasedVersionMojo
    extends AbstractDefinePropertyMojo
{

    /**
     * The artifact metadata source to use.
     */
    @Component
    private RepositoryMetadataManager repositoryMetadataManager;


    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    @Parameter( defaultValue = "${localRepository}", readonly = true )
    private ArtifactRepository localRepository;

    @Parameter( defaultValue = "${project.remoteArtifactRepositories}", readonly = true )
    private List<ArtifactRepository> remoteArtifactRepositories;

    /**
     * Prefix string to use for the set of version properties.
     */
    @Parameter( defaultValue = "releasedVersion" )
    private String propertyPrefix;

    private void defineVersionProperty( String name, String value )
    {
        defineProperty( propertyPrefix + '.' + name, Objects.toString( value, "" ) );
    }

    private void defineVersionProperty( String name, int value )
    {
        defineVersionProperty( name, Integer.toString( value ) );
    }

    public void execute()
    {
        try
        {
            /*
             * We use a dummy version "0" here to check for all released version.
             * Reason: The current project's version is completely irrelevant for the check to retrieve all available versions.
             * But if the current project's version is a -SNAPSHOT version, only repository from maven settings are
             * requested that are allowed for snapshots - but we want to query for released versions, not for snapshots.
             * Using the dummy version "0" which looks like a released version, the repos with releases are requested.
             * see https://github.com/mojohaus/build-helper-maven-plugin/issues/108
             */

            MetadataResolutionRequest request = new DefaultMetadataResolutionRequest()
                    .setArtifact(new DefaultArtifact(
                            getProject().getGroupId(), getProject().getArtifactId(),
                            VersionRange.createFromVersion("0"),
                            "", "", null,
                            artifactHandlerManager.getArtifactHandler(""),
                            false))
                    .setLocalRepository(localRepository)
                    .setRemoteRepositories(remoteArtifactRepositories);

            RepositoryMetadata metadata = new ArtifactRepositoryMetadata( request.getArtifact() );

            repositoryMetadataManager.resolve( metadata, request);

            List<String> availableVersions = request.getLocalRepository().findVersions( request.getArtifact() );
            Metadata repoMetadata = metadata.getMetadata();
            Collection<String> versions1 = new LinkedHashSet<>();
            if ( ( repoMetadata != null ) && ( repoMetadata.getVersioning() != null ) )
            {
                versions1.addAll( repoMetadata.getVersioning().getVersions() );
            }

            versions1.addAll(availableVersions);
            List<ArtifactVersion> versions = versions1.stream().map(DefaultArtifactVersion::new).collect(Collectors.toList());

            ArtifactVersion releasedVersion = null;
            for ( ArtifactVersion version : versions )
            {
                if ( !ArtifactUtils.isSnapshot( version.toString() )
                    && ( releasedVersion == null || version.compareTo( releasedVersion ) > 0 ) )
                {
                    releasedVersion = version;
                }
            }

            if ( releasedVersion != null )
            {
                // Use ArtifactVersion.toString(), the major, minor and incrementalVersion return all an int.
                String releasedVersionValue = releasedVersion.toString();

                // This would not always reflect the expected version.
                int dashIndex = releasedVersionValue.indexOf( '-' );
                if ( dashIndex >= 0 )
                {
                    releasedVersionValue = releasedVersionValue.substring( 0, dashIndex );
                }

                defineVersionProperty( "version", releasedVersionValue );
                defineVersionProperty( "majorVersion", releasedVersion.getMajorVersion() );
                defineVersionProperty( "minorVersion", releasedVersion.getMinorVersion() );
                defineVersionProperty( "incrementalVersion", releasedVersion.getIncrementalVersion() );
                defineVersionProperty( "buildNumber", releasedVersion.getBuildNumber() );
                defineVersionProperty( "qualifier", releasedVersion.getQualifier() );
            }
            else {
                getLog().debug("No released version found.");
            }
        }
        catch ( RepositoryMetadataResolutionException e ) {
            getLog().warn("Failed to retrieve artifacts metadata, cannot resolve the released version");
        }
    }
}
