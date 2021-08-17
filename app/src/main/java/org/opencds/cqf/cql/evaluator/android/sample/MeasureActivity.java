package org.opencds.cqf.cql.evaluator.android.sample;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.common.collect.Lists;

import org.apache.commons.lang3.tuple.Pair;
import org.cqframework.cql.cql2elm.CqlTranslatorOptions;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Patient;
import org.opencds.cqf.cql.engine.data.CompositeDataProvider;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.fhir.converter.FhirTypeConverter;
import org.opencds.cqf.cql.engine.fhir.converter.FhirTypeConverterFactory;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.cql.engine.model.ModelResolver;
import org.opencds.cqf.cql.evaluator.CqlEvaluator;
import org.opencds.cqf.cql.evaluator.cql2elm.content.LibraryContentProvider;
import org.opencds.cqf.cql.evaluator.cql2elm.content.fhir.BundleFhirLibraryContentProvider;
import org.opencds.cqf.cql.evaluator.cql2elm.util.LibraryVersionSelector;
import org.opencds.cqf.cql.evaluator.engine.execution.TranslatingLibraryLoader;
import org.opencds.cqf.cql.evaluator.engine.retrieve.BundleRetrieveProvider;
import org.opencds.cqf.cql.evaluator.engine.terminology.BundleTerminologyProvider;
import org.opencds.cqf.cql.evaluator.fhir.adapter.r4.AdapterFactory;
import org.opencds.cqf.cql.evaluator.library.CqlFhirParametersConverter;
import org.opencds.cqf.cql.evaluator.library.LibraryEvaluator;
import org.opencds.cqf.cql.evaluator.measure.r4.MeasureProcessor;
import org.opencds.cqf.cql.evaluator.measure.r4.R4MeasureEvaluation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.api.BundleInclusionRule;
import ca.uhn.fhir.model.valueset.BundleTypeEnum;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.BundleLinks;
import ca.uhn.fhir.rest.api.IVersionSpecificBundleFactory;

public class MeasureActivity extends AppCompatActivity {

    FhirContext fhirContext = FhirContext.forCached(FhirVersionEnum.R4);
    AdapterFactory adapterFactory = new AdapterFactory();
    FhirTypeConverter fhirTypeConverter;
    CqlFhirParametersConverter cqlFhirParametersConverter;
    LibraryVersionSelector libraryVersionSelector;

    LibraryContentProvider contentProvider;
    ModelResolver modelResolver;
    BundleTerminologyProvider terminologyProvider;
    BundleRetrieveProvider bundleRetrieveProvider;

    IParser parser;

    CqlEvaluator cqlEvaluator;
    LibraryEvaluator libraryEvaluator;

    MeasureProcessor measureProcessor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measure);

        this.parser = this.fhirContext.newJsonParser();
        this.parser.setPrettyPrint(true);

        this.libraryVersionSelector = new LibraryVersionSelector(this.adapterFactory);
        this.fhirTypeConverter = new FhirTypeConverterFactory().create(fhirContext.getVersion().getVersion());
        this.cqlFhirParametersConverter = new CqlFhirParametersConverter(this.fhirContext, this.adapterFactory, this.fhirTypeConverter);
        try {
            // Load Library Content and create a LibraryContentProvider, which is the interface used by the LibraryLoader for getting library CQL/ELM/etc.
            InputStream libraryStream = this.getAssets().open("library/Library-ANCRecommendationA2.json");
            InputStream fhirHelpersStream = this.getAssets().open("library/Library-FHIRHelpers.json");
            IBaseResource library = this.parser.parseResource(libraryStream);
            IBaseResource fhirHelpersLibrary = this.parser.parseResource(fhirHelpersStream);

            List<IBaseResource> resources = Lists.newArrayList(library, fhirHelpersLibrary);
            IVersionSpecificBundleFactory bundleFactory = this.fhirContext.newBundleFactory();

            BundleLinks bundleLinks = new BundleLinks("", null, true, BundleTypeEnum.COLLECTION);

            bundleFactory.addRootPropertiesToBundle("bundled-directory", bundleLinks, resources.size(), null);

            bundleFactory.addResourcesToBundle(resources, BundleTypeEnum.COLLECTION, "",
                    BundleInclusionRule.BASED_ON_INCLUDES, null);

            this.contentProvider = new BundleFhirLibraryContentProvider(this.fhirContext, (IBaseBundle) bundleFactory.getResourceBundle(), this.adapterFactory, this.libraryVersionSelector);

            // Load terminology content, and create a TerminologyProvider which is the interface used by the evaluator for resolving terminology
            InputStream valueSetStream = this.getAssets().open("valueset/valueset-bundle.json");
            IBaseResource valueSetBundle = this.parser.parseResource(valueSetStream);
            this.terminologyProvider = new BundleTerminologyProvider(this.fhirContext, (IBaseBundle) valueSetBundle);

            // Load data content, and create a RetrieveProvider which is the interface used for implementations of CQL retrieves.
            InputStream dataStream = this.getAssets().open("test/mom-with-anaemia-bundle.json");
            IBaseResource dataBundle = this.parser.parseResource(dataStream);
            this.bundleRetrieveProvider = new BundleRetrieveProvider(this.fhirContext, (IBaseBundle) dataBundle);
            this.bundleRetrieveProvider.setTerminologyProvider(this.terminologyProvider);
            this.bundleRetrieveProvider.setExpandValueSets(true);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        this.cqlEvaluator = new CqlEvaluator(
                new TranslatingLibraryLoader(
                        new ModelManager(),
                        Collections.singletonList(this.contentProvider),
                        CqlTranslatorOptions.defaultOptions()) {

                    // This is a hack needed to circumvent a bug that's currently present in the cql-engine.
                    // By default, the LibraryLoader checks to ensure that the same translator options are used to for all libraries,
                    // And it will re-translate if possible. Since translating CQL is not currently possible
                    // on Android (some changes to the way ModelInfos are loaded is needed) the library loader just needs to load libraries
                    // regardless of whether the options match.
                    @Override
                    protected Boolean translatorOptionsMatch(Library library) {
                        return true;
//                        EnumSet<CqlTranslator.Options> options = TranslatorOptionsUtil.getTranslatorOptions(library);
//                        if (options == null) {
//                            return false;
//                        }
//
//                        return options.equals(this.cqlTranslatorOptions.getOptions());
                    }
                },
                new HashMap<String, DataProvider>() {{
                    put("http://hl7.org/fhir", new CompositeDataProvider(new R4FhirModelResolver(), bundleRetrieveProvider));
                }}, this.terminologyProvider);

        this.libraryEvaluator = new LibraryEvaluator(this.cqlFhirParametersConverter, cqlEvaluator);
    }

    public void runCql(View view) {
        IBaseParameters result = libraryEvaluator.evaluate(new VersionedIdentifier().withId("ANCRecommendationA2"), Pair.of("Patient", "mom-with-anaemia"), null, null);

        String parameters = this.fhirContext.newJsonParser().encodeResourceToString(result);
        TextView output = (TextView) this.findViewById(R.id.output_text);
        output.setText(parameters);
    }
}