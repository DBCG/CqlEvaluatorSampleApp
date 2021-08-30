package org.opencds.cqf.cql.evaluator.android.sample;

import android.content.res.AssetManager;

import org.apache.commons.io.FileUtils;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.opencds.cqf.cql.evaluator.fhir.DirectoryBundler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.api.BundleInclusionRule;
import ca.uhn.fhir.model.valueset.BundleTypeEnum;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.BundleLinks;
import ca.uhn.fhir.rest.api.IVersionSpecificBundleFactory;
import ca.uhn.fhir.util.BundleUtil;

public class AssetBundler {

    private static Bundle assetBundle = null;

    private static Bundle dataBundle = null;

    private static IParser xml = null;
    private static IParser json = null;


    public static synchronized Bundle getAssetBundle(FhirContext fhirContext, AssetManager assetManager) {
        if (assetBundle == null){
            assetBundle = (Bundle)generateAssetBundle(fhirContext, assetManager, "resources");
        }

        return assetBundle;
    }

    public static synchronized Bundle getDataBundle(FhirContext fhirContext, AssetManager assetManager) {
        if (dataBundle == null){
            dataBundle = (Bundle)generateAssetBundle(fhirContext, assetManager, "tests");
        }

        return dataBundle;
    }

    public static synchronized Bundle getTerminologyBundle(FhirContext fhirContext, AssetManager assetManager) {
        if (dataBundle == null){
            dataBundle = (Bundle)generateAssetBundle(fhirContext, assetManager, "vocabulary/valueset");
        }

        return dataBundle;
    }


    private static IBaseBundle generateAssetBundle(FhirContext fhirContext, AssetManager assetManager, String root) {

        List<String> files = null;
        try {
            files = recurse(assetManager, root, assetManager.list(root));
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to generate asset bundle", e);
        }

        return bundleFiles(fhirContext, assetManager, root, files);
    }

    private static List<String> recurse(AssetManager assetManager, String root, String[] files) throws IOException {
        List<String> returnFiles = new ArrayList<>();
        for (String f : files) {
            String fullPath = root + "/" + f;
            if (f.endsWith(".xml") || f.endsWith(".json")) {
                returnFiles.add(fullPath);
            }
            else {
                returnFiles.addAll(recurse(assetManager, fullPath, assetManager.list(fullPath)));
            }
        }

        return returnFiles;
    }


    private static IBaseBundle bundleFiles(FhirContext fhirContext, AssetManager assetManager, String root, List<String> files) {
        List<IBaseResource> resources = new ArrayList<>();

        for (String f : files) {

            if (!f.endsWith(".xml") && !f.endsWith(".json")) {
                continue;
            }

            IBaseResource resource = parseFile(assetManager, fhirContext, f);

            if (resource == null) {
                continue;
            }

            if (resource instanceof IBaseBundle) {
                List<IBaseResource> innerResources = flatten(fhirContext, (IBaseBundle) resource);
                resources.addAll(innerResources);
            } else {
                resources.add(resource);
            }
        }

        IVersionSpecificBundleFactory bundleFactory = fhirContext.newBundleFactory();

        BundleLinks bundleLinks = new BundleLinks(root, null, true, BundleTypeEnum.COLLECTION);

        bundleFactory.addRootPropertiesToBundle("bundled-directory", bundleLinks, resources.size(), null);

        bundleFactory.addResourcesToBundle(resources, BundleTypeEnum.COLLECTION, "",
                BundleInclusionRule.BASED_ON_INCLUDES, null);

        return (IBaseBundle) bundleFactory.getResourceBundle();
    }

    private static IBaseResource parseFile(AssetManager assetManager, FhirContext fhirContext, String f) {
        try {
            InputStream resource = assetManager.open(f);

            IParser selectedParser = selectParser(fhirContext, f);
            return selectedParser.parseResource(resource);
        } catch (Exception e) {
            return null;
        }
    }

    private static IParser selectParser(FhirContext fhirContext, String filename) {
        if (filename.toLowerCase().endsWith("json")) {
            if (json == null) {
                json = fhirContext.newJsonParser();
            }

            return json;
        } else {
            if (xml == null) {
                xml = fhirContext.newXmlParser();
            }

            return xml;
        }

    }

    private static List<IBaseResource> flatten(FhirContext fhirContext, IBaseBundle bundle) {
        List<IBaseResource> resources = new ArrayList<>();

        List<IBaseResource> bundleResources = BundleUtil.toListOfResources(fhirContext, bundle);
        for (IBaseResource r : bundleResources) {
            if (r instanceof IBaseBundle) {
                List<IBaseResource> innerResources = flatten(fhirContext, (IBaseBundle) r);
                resources.addAll(innerResources);
            } else {
                resources.add(r);
            }
        }

        return resources;
    }



}
