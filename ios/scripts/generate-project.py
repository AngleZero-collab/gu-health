#!/usr/bin/env python3
"""Generate the checked-in Xcode project with Python 3; no third-party tools or packages."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
objects = {}
def uid(key):
    return hashlib.sha256(key.encode()).hexdigest()[:24].upper()
def obj(key, kind, **values):
    identity = uid(key)
    objects[identity] = {"isa": kind, **values}
    return identity
def quoted(text):
    return json.dumps(str(text), ensure_ascii=False)
def render(value):
    if isinstance(value, dict):
        return "{ " + " ".join(f"{quoted(k)} = {render(v)};" for k, v in value.items()) + " }"
    if isinstance(value, list):
        return "(" + ", ".join(render(v) for v in value) + ")"
    return str(value) if isinstance(value, int) else quoted(value)

sources = sorted((ROOT / "GuHealth").rglob("*.swift"))
source_refs, source_build = [], []
for path in sources:
    name = str(path.relative_to(ROOT))
    ref = obj(name, "PBXFileReference", lastKnownFileType="sourcecode.swift", path=name, sourceTree="<group>")
    source_refs.append(ref)
    source_build.append(obj("build:"+name, "PBXBuildFile", fileRef=ref))
resource_refs, resource_build = [], []
for name, kind in [("GuHealth/Resources/Assets.xcassets", "folder.assetcatalog"), ("GuHealth/Resources/PrivacyInfo.xcprivacy", "text.xml")]:
    ref = obj(name, "PBXFileReference", lastKnownFileType=kind, path=name, sourceTree="<group>")
    resource_refs.append(ref); resource_build.append(obj("build:"+name,"PBXBuildFile",fileRef=ref))
info = obj("info", "PBXFileReference", lastKnownFileType="text.plist.xml", path="GuHealth/Resources/Info.plist", sourceTree="<group>")
app = obj("app-product", "PBXFileReference", explicitFileType="wrapper.application", path="GuHealth.app", sourceTree="BUILT_PRODUCTS_DIR")
ui_product = obj("ui-product", "PBXFileReference", explicitFileType="wrapper.cfbundle", path="GuHealthUITests.xctest", sourceTree="BUILT_PRODUCTS_DIR")
products = obj("products", "PBXGroup", children=[app, ui_product], name="Products", sourceTree="<group>")
ui_ref = obj("ui-source", "PBXFileReference", lastKnownFileType="sourcecode.swift", path="GuHealthUITests/GuHealthUITests.swift", sourceTree="<group>")
main_group = obj("main-group", "PBXGroup", children=source_refs+resource_refs+[info,ui_ref,products], sourceTree="<group>")
source_phase = obj("sources", "PBXSourcesBuildPhase", buildActionMask=2147483647, files=source_build, runOnlyForDeploymentPostprocessing=0)
resource_phase = obj("resources", "PBXResourcesBuildPhase", buildActionMask=2147483647, files=resource_build, runOnlyForDeploymentPostprocessing=0)
framework_phase = obj("frameworks", "PBXFrameworksBuildPhase", buildActionMask=2147483647, files=[], runOnlyForDeploymentPostprocessing=0)
ui_source_phase = obj("ui-sources", "PBXSourcesBuildPhase", buildActionMask=2147483647, files=[obj("build-ui", "PBXBuildFile", fileRef=ui_ref)], runOnlyForDeploymentPostprocessing=0)

def configuration_list(key, settings):
    configs = []
    for name in ("Debug", "Release"):
        current = dict(settings)
        current["SWIFT_OPTIMIZATION_LEVEL"] = "-Onone" if name == "Debug" else "-O"
        current["SWIFT_ACTIVE_COMPILATION_CONDITIONS"] = "DEBUG" if name == "Debug" else ""
        if name == "Debug": current["ENABLE_TESTABILITY"] = "YES"
        configs.append(obj(key+name, "XCBuildConfiguration", buildSettings=current, name=name))
    return obj(key+"-list", "XCConfigurationList", buildConfigurations=configs, defaultConfigurationIsVisible=0, defaultConfigurationName="Release")

common = {"CLANG_ENABLE_MODULES":"YES", "SWIFT_VERSION":"5.0", "IPHONEOS_DEPLOYMENT_TARGET":"17.0", "SDKROOT":"iphoneos",
          "SUPPORTED_PLATFORMS":"iphoneos iphonesimulator", "TARGETED_DEVICE_FAMILY":"1,2", "CODE_SIGN_STYLE":"Automatic",
          "ENABLE_USER_SCRIPT_SANDBOXING":"YES", "CLANG_ENABLE_OBJC_ARC":"YES", "GCC_WARN_UNDECLARED_SELECTOR":"YES"}
project_configs = configuration_list("project", common)
app_configs = configuration_list("app", {"PRODUCT_BUNDLE_IDENTIFIER":"com.gucare.health", "PRODUCT_NAME":"$(TARGET_NAME)",
    "MARKETING_VERSION":"1.0.0", "CURRENT_PROJECT_VERSION":"1", "INFOPLIST_FILE":"GuHealth/Resources/Info.plist",
    "ASSETCATALOG_COMPILER_APPICON_NAME":"AppIcon", "GENERATE_INFOPLIST_FILE":"NO", "SWIFT_EMIT_LOC_STRINGS":"YES"})
app_target = obj("app-target", "PBXNativeTarget", buildConfigurationList=app_configs, buildPhases=[source_phase,framework_phase,resource_phase],
                 buildRules=[], dependencies=[], name="GuHealth", productName="GuHealth", productReference=app, productType="com.apple.product-type.application")
ui_configs = configuration_list("ui", {"PRODUCT_BUNDLE_IDENTIFIER":"com.gucare.health.uitests", "PRODUCT_NAME":"$(TARGET_NAME)",
    "GENERATE_INFOPLIST_FILE":"YES", "TEST_TARGET_NAME":"GuHealth", "LD_RUNPATH_SEARCH_PATHS":["$(inherited)","@executable_path/Frameworks","@loader_path/Frameworks"]})
proxy = obj("ui-proxy", "PBXContainerItemProxy", containerPortal=uid("project"), proxyType=1, remoteGlobalIDString=app_target, remoteInfo="GuHealth")
dependency = obj("ui-dependency", "PBXTargetDependency", target=app_target, targetProxy=proxy)
ui_target = obj("ui-target", "PBXNativeTarget", buildConfigurationList=ui_configs, buildPhases=[ui_source_phase], buildRules=[], dependencies=[dependency],
                name="GuHealthUITests", productName="GuHealthUITests", productReference=ui_product, productType="com.apple.product-type.bundle.ui-testing")
project = obj("project", "PBXProject", attributes={"LastUpgradeCheck":"1600", "TargetAttributes":{app_target:{"CreatedOnToolsVersion":"16.0"},ui_target:{"CreatedOnToolsVersion":"16.0", "TestTargetID":app_target}}},
              buildConfigurationList=project_configs, compatibilityVersion="Xcode 14.0", developmentRegion="zh_TW", hasScannedForEncodings=0,
              knownRegions=["zh_TW","Base"], mainGroup=main_group, productRefGroup=products, projectDirPath="", projectRoot="", targets=[app_target,ui_target])
project_file = ROOT/"GuHealth.xcodeproj/project.pbxproj"
project_file.parent.mkdir(parents=True, exist_ok=True)
project_file.write_text("// !$*UTF8*$!\n"+render({"archiveVersion":1,"classes":{},"objectVersion":56,"objects":objects,"rootObject":project})+"\n")
scheme = f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
 <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
  <BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{app_target}" BuildableName="GuHealth.app" BlueprintName="GuHealth" ReferencedContainer="container:GuHealth.xcodeproj"/></BuildActionEntry>
 </BuildActionEntries></BuildAction>
 <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables>
  <TestableReference skipped="NO"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ui_target}" BuildableName="GuHealthUITests.xctest" BlueprintName="GuHealthUITests" ReferencedContainer="container:GuHealth.xcodeproj"/></TestableReference>
 </Testables></TestAction>
 <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{app_target}" BuildableName="GuHealth.app" BlueprintName="GuHealth" ReferencedContainer="container:GuHealth.xcodeproj"/></BuildableProductRunnable></LaunchAction>
 <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{app_target}" BuildableName="GuHealth.app" BlueprintName="GuHealth" ReferencedContainer="container:GuHealth.xcodeproj"/></BuildableProductRunnable></ProfileAction>
 <AnalyzeAction buildConfiguration="Debug"/>
 <ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
'''
scheme_path = ROOT/"GuHealth.xcodeproj/xcshareddata/xcschemes/GuHealth.xcscheme"
scheme_path.parent.mkdir(parents=True, exist_ok=True); scheme_path.write_text(scheme)
print(f"Generated GuHealth.xcodeproj ({len(sources)} Swift files, no external dependencies).")
