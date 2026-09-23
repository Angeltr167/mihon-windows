[CmdletBinding()]
param(
    [switch] $CheckOnly,
    [switch] $AllowExternalTools,
    [switch] $Stacktrace,
    [string[]] $Tasks
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$expectedRoot = 'C:\Projects\mihon-windows'

if (-not [string]::Equals([System.IO.Path]::GetFullPath((Get-Location).Path), $expectedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Run this script only from $expectedRoot."
}

if (-not [string]::Equals($repositoryRoot, $expectedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'The validation script is not located in the expected repository.'
}

$requiredPaths = @(
    @{ Name = 'MIHON_WORKSPACE_JDK21'; Kind = 'jdk'; Version = 21; Description = 'JDK 21 runtime' },
    @{ Name = 'MIHON_WORKSPACE_JDK17'; Kind = 'jdk'; Version = 17; Description = 'JDK 17 toolchain' },
    @{ Name = 'MIHON_WORKSPACE_ANDROID_SDK'; Kind = 'directory'; Description = 'Android SDK' },
    @{ Name = 'MIHON_WORKSPACE_GRADLE_HOME'; Kind = 'directory'; Description = 'Gradle user home and offline cache' }
)

function Resolve-ConfiguredDirectory {
    param(
        [Parameter(Mandatory)] [string] $VariableName,
        [Parameter(Mandatory)] [string] $Description
    )

    $configuredPath = [Environment]::GetEnvironmentVariable($VariableName, 'Process')
    if ([string]::IsNullOrWhiteSpace($configuredPath)) {
        throw "Missing prerequisite: set $VariableName to an existing path inside the repository ($Description)."
    }

    if ([System.IO.Path]::IsPathRooted($configuredPath)) {
        $candidatePath = [System.IO.Path]::GetFullPath($configuredPath).TrimEnd('\')
    } else {
        $candidatePath = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $configuredPath)).TrimEnd('\')
    }
    $rootPrefix = $repositoryRoot.TrimEnd('\') + '\'
    if (-not $AllowExternalTools -and -not $candidatePath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Invalid prerequisite: $VariableName must resolve to a descendant of the repository root."
    }

    if (-not $AllowExternalTools) {
        $relativePath = $candidatePath.Substring($rootPrefix.Length)
        $currentPath = $repositoryRoot
        foreach ($part in $relativePath.Split([char[]]@('\'), [System.StringSplitOptions]::RemoveEmptyEntries)) {
            $currentPath = Join-Path $currentPath $part
            if (-not (Test-Path -LiteralPath $currentPath)) {
                throw "Missing prerequisite: $VariableName does not identify an existing in-repository path ($Description)."
            }
            $component = Get-Item -LiteralPath $currentPath -Force -ErrorAction Stop
            if (($component.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Invalid prerequisite: $VariableName must not contain a link or junction."
            }
        }
    }

    try {
        $fullPath = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $candidatePath -ErrorAction Stop).Path).TrimEnd('\')
    } catch {
        throw "Missing prerequisite: $VariableName does not identify an existing in-repository path ($Description)."
    }
    if (-not $AllowExternalTools -and -not $fullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Invalid prerequisite: $VariableName must resolve to a descendant of the repository root."
    }

    $item = Get-Item -LiteralPath $fullPath -ErrorAction Stop
    if (-not $item.PSIsContainer) {
        throw "Invalid prerequisite: $VariableName must identify a directory."
    }

    return $fullPath
}

function Assert-WorkspaceOutputPath {
    param([Parameter(Mandatory)] [string] $Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $rootPrefix = $repositoryRoot.TrimEnd('\') + '\'
    if (-not $fullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'A validation output path resolved outside the repository.'
    }

    $relativePath = $fullPath.Substring($rootPrefix.Length)
    $currentPath = $repositoryRoot
    foreach ($part in $relativePath.Split('\', [System.StringSplitOptions]::RemoveEmptyEntries)) {
        $currentPath = Join-Path $currentPath $part
        if (Test-Path -LiteralPath $currentPath) {
            $item = Get-Item -LiteralPath $currentPath -Force
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Validation output path contains a link or junction: $part."
            }
        }
    }

    return $fullPath
}

function Stop-WithBlocker {
    param([Parameter(Mandatory)] [string] $Message)

    [Console]::Error.WriteLine("EXTERNAL CONTEXT REQUIRED: $Message")
    exit 2
}

try {
    $configured = @{}
    foreach ($entry in $requiredPaths) {
        $configured[$entry.Name] = Resolve-ConfiguredDirectory -VariableName $entry.Name -Description $entry.Description
        if ($entry.Kind -eq 'jdk' -and -not (Test-Path -LiteralPath (Join-Path $configured[$entry.Name] 'bin\java.exe') -PathType Leaf)) {
            throw "Missing prerequisite: $($entry.Name) must contain bin\java.exe."
        }
        if ($entry.Kind -eq 'jdk') {
            $releaseFile = Join-Path $configured[$entry.Name] 'release'
            if (-not (Test-Path -LiteralPath $releaseFile -PathType Leaf)) {
                throw "Missing prerequisite: $($entry.Name) must contain a JDK release file."
            }
            $releaseContent = Get-Content -LiteralPath $releaseFile -Raw
            if ($releaseContent -notmatch "(?m)^JAVA_VERSION=`"$($entry.Version)(?:[.`"])" ) {
                throw "Invalid prerequisite: $($entry.Name) must be JDK $($entry.Version)."
            }
        }
    }

    $sdkVersions = Get-Content -LiteralPath (Join-Path $repositoryRoot 'gradle\mihon.versions.toml') -Raw
    if ($sdkVersions -notmatch '(?m)^android-sdk-compile\s*=\s*"([0-9.]+)"\s*$') {
        throw 'Unable to determine the required Android compile SDK from repository-local configuration.'
    }
    $compileSdk = $Matches[1]
    $androidJar = Join-Path $configured.MIHON_WORKSPACE_ANDROID_SDK "platforms\android-$compileSdk\android.jar"
    if (-not (Test-Path -LiteralPath $androidJar -PathType Leaf)) {
        throw "Missing prerequisite: MIHON_WORKSPACE_ANDROID_SDK must contain the Android $compileSdk platform."
    }

    $wrapperProperties = Get-Content -LiteralPath (Join-Path $repositoryRoot 'gradle\wrapper\gradle-wrapper.properties') -Raw
    if ($wrapperProperties -notmatch '(?m)^distributionUrl=.*gradle-([0-9][0-9A-Za-z.]+)-bin\.zip\s*$') {
        throw 'Unable to determine the required Gradle wrapper distribution from repository-local configuration.'
    }
    $gradleVersion = $Matches[1]
    $wrapperDistributionCache = Join-Path $configured.MIHON_WORKSPACE_GRADLE_HOME "wrapper\dists\gradle-$gradleVersion-bin"
    if (-not (Test-Path -LiteralPath $wrapperDistributionCache -PathType Container)) {
        throw "Missing prerequisite: the offline Gradle $gradleVersion wrapper distribution must already exist under MIHON_WORKSPACE_GRADLE_HOME."
    }

    $distributionReady = $false
    foreach ($hashDirectory in Get-ChildItem -LiteralPath $wrapperDistributionCache -Directory -Force) {
        if (($hashDirectory.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            continue
        }
        $gradleBatch = Join-Path $hashDirectory.FullName "gradle-$gradleVersion\bin\gradle.bat"
        $installedMarker = Join-Path $hashDirectory.FullName "gradle-$gradleVersion-bin.zip.ok"
        if ((Test-Path -LiteralPath $gradleBatch -PathType Leaf) -and (Test-Path -LiteralPath $installedMarker -PathType Leaf)) {
            $distributionReady = $true
            break
        }
    }
    if (-not $distributionReady) {
        throw "Missing prerequisite: the offline Gradle $gradleVersion distribution is not unpacked under MIHON_WORKSPACE_GRADLE_HOME."
    }

    $workspaceRoot = Assert-WorkspaceOutputPath -Path (Join-Path $repositoryRoot 'build\workspace')
    $workspaceHome = Assert-WorkspaceOutputPath -Path (Join-Path $workspaceRoot 'home')
    $workspaceTemp = Assert-WorkspaceOutputPath -Path (Join-Path $workspaceRoot 'temp')
    $workspaceAndroidUser = Assert-WorkspaceOutputPath -Path (Join-Path $workspaceRoot 'android-user')
    $workspaceBrowserProfile = Assert-WorkspaceOutputPath -Path (Join-Path $workspaceRoot 'browser-profile')
    $workspaceBrowsers = Assert-WorkspaceOutputPath -Path (Join-Path $workspaceRoot 'browsers')
    $workspaceKotlinDaemon = Assert-WorkspaceOutputPath -Path (Join-Path $workspaceRoot 'kotlin-daemon')

    if ($CheckOnly) {
        if ($AllowExternalTools) {
            Write-Output 'CheckOnly passed: explicit external tool paths are present; validation output paths are repository-local.'
        } else {
            Write-Output 'CheckOnly passed: configured toolchains, SDK, offline Gradle distribution, and output paths are repository-local.'
        }
        exit 0
    }

    if ($null -eq $Tasks -or $Tasks.Count -eq 0) {
        throw 'Provide one or more Gradle task names with -Tasks.'
    }
    foreach ($task in $Tasks) {
        if ($task -notmatch '^(?:[A-Za-z][A-Za-z0-9_.-]*|(?::[A-Za-z0-9_.-]+)+)$') {
            throw "Invalid Gradle task name: task arguments must not contain options or shell syntax."
        }
        if ($task -match '(^|:)desktopApp:run$') {
            throw 'The normal Desktop app run task is not allowed by the workspace validation runner.'
        }
    }
} catch {
    Stop-WithBlocker -Message $_.Exception.Message
}

$environmentNames = @(
    'JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT', 'ANDROID_USER_HOME', 'ANDROID_PREFS_ROOT',
    'ANDROID_SDK_HOME', 'GRADLE_USER_HOME', 'HOME', 'USERPROFILE', 'APPDATA', 'LOCALAPPDATA',
    'TEMP', 'TMP', 'PLAYWRIGHT_BROWSERS_PATH', 'MIHON_DESKTOP_HOME', 'MIHON_BROWSER_PROFILE',
    'KOTLIN_DAEMON_RUN_FILES_PATH', 'JAVA_TOOL_OPTIONS'
)
$previousEnvironment = @{}
foreach ($name in $environmentNames) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    $directories = @($workspaceHome, $workspaceTemp, $workspaceAndroidUser, $workspaceBrowserProfile, $workspaceBrowsers, $workspaceKotlinDaemon)
    foreach ($directory in $directories) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }

    $jdk21 = $configured.MIHON_WORKSPACE_JDK21
    $jdk17 = $configured.MIHON_WORKSPACE_JDK17
    $toolchainPaths = "$jdk21,$jdk17"
    $javaToolOptions = "-Duser.home=$workspaceHome -Djava.io.tmpdir=$workspaceTemp -Dmihon.desktop.home=$(Join-Path $workspaceRoot 'mihon-home') -Dmihon.browser.profile=$workspaceBrowserProfile"

    [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk21, 'Process')
    [Environment]::SetEnvironmentVariable('ANDROID_HOME', $configured.MIHON_WORKSPACE_ANDROID_SDK, 'Process')
    [Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $configured.MIHON_WORKSPACE_ANDROID_SDK, 'Process')
    [Environment]::SetEnvironmentVariable('ANDROID_USER_HOME', $workspaceAndroidUser, 'Process')
    Remove-Item -LiteralPath 'Env:ANDROID_PREFS_ROOT' -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath 'Env:ANDROID_SDK_HOME' -ErrorAction SilentlyContinue
    [Environment]::SetEnvironmentVariable('GRADLE_USER_HOME', $configured.MIHON_WORKSPACE_GRADLE_HOME, 'Process')
    [Environment]::SetEnvironmentVariable('HOME', $workspaceHome, 'Process')
    [Environment]::SetEnvironmentVariable('USERPROFILE', $workspaceHome, 'Process')
    [Environment]::SetEnvironmentVariable('APPDATA', (Join-Path $workspaceHome 'Roaming'), 'Process')
    [Environment]::SetEnvironmentVariable('LOCALAPPDATA', (Join-Path $workspaceHome 'Local'), 'Process')
    [Environment]::SetEnvironmentVariable('TEMP', $workspaceTemp, 'Process')
    [Environment]::SetEnvironmentVariable('TMP', $workspaceTemp, 'Process')
    [Environment]::SetEnvironmentVariable('PLAYWRIGHT_BROWSERS_PATH', $workspaceBrowsers, 'Process')
    [Environment]::SetEnvironmentVariable('MIHON_DESKTOP_HOME', (Join-Path $workspaceRoot 'mihon-home'), 'Process')
    [Environment]::SetEnvironmentVariable('MIHON_BROWSER_PROFILE', $workspaceBrowserProfile, 'Process')
    [Environment]::SetEnvironmentVariable('KOTLIN_DAEMON_RUN_FILES_PATH', $workspaceKotlinDaemon, 'Process')
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', $javaToolOptions, 'Process')

    $gradleArguments = @(
        '--offline',
        '--no-daemon',
        '-Dorg.gradle.java.installations.auto-detect=false',
        '-Dorg.gradle.java.installations.auto-download=false',
        "-Dorg.gradle.java.installations.paths=$toolchainPaths",
        '-Pandroid.builder.sdkDownload=false',
        '-Pmihon.workspace.validation=true'
    )
    if ($Stacktrace) {
        $gradleArguments += '--stacktrace'
    }
    $gradleArguments += $Tasks

    & (Join-Path $repositoryRoot 'gradlew.bat') @gradleArguments
    $gradleExitCode = $LASTEXITCODE
    exit $gradleExitCode
} finally {
    foreach ($name in $environmentNames) {
        if ($null -eq $previousEnvironment[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
        }
    }
}
