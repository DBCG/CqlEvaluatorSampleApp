package org.opencds.cqf.cql.evaluator.android.sample;

import static org.opencds.cqf.cql.evaluator.converter.VersionedIdentifierConverter.toElmIdentifier;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.lang3.tuple.Pair;
import org.cqframework.cql.cql2elm.CqlTranslatorOptions;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.opencds.cqf.cql.engine.data.CompositeDataProvider;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.fhir.converter.FhirTypeConverter;
import org.opencds.cqf.cql.engine.fhir.converter.FhirTypeConverterFactory;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.cql.evaluator.CqlEvaluator;
import org.opencds.cqf.cql.evaluator.cql2elm.content.LibraryContentProvider;
import org.opencds.cqf.cql.evaluator.cql2elm.content.LibraryContentType;
import org.opencds.cqf.cql.evaluator.cql2elm.content.fhir.BundleFhirLibraryContentProvider;
import org.opencds.cqf.cql.evaluator.cql2elm.util.LibraryVersionSelector;
import org.opencds.cqf.cql.evaluator.engine.execution.TranslatingLibraryLoader;
import org.opencds.cqf.cql.evaluator.engine.retrieve.BundleRetrieveProvider;
import org.opencds.cqf.cql.evaluator.engine.terminology.BundleTerminologyProvider;
import org.opencds.cqf.cql.evaluator.fhir.adapter.r4.AdapterFactory;
import org.opencds.cqf.cql.evaluator.library.CqlFhirParametersConverter;
import org.opencds.cqf.cql.evaluator.library.LibraryEvaluator;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.parser.IParser;

public class LibraryActivity extends AppCompatActivity {

    FhirContext fhirContext = FhirContext.forCached(FhirVersionEnum.R4);
    AdapterFactory adapterFactory = new AdapterFactory();
    FhirTypeConverter fhirTypeConverter;
    CqlFhirParametersConverter cqlFhirParametersConverter;
    LibraryVersionSelector libraryVersionSelector;

    LibraryContentProvider contentProvider;
    BundleTerminologyProvider terminologyProvider;
    BundleRetrieveProvider bundleRetrieveProvider;

    IParser parser;

    CqlEvaluator cqlEvaluator;
    LibraryEvaluator libraryEvaluator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        this.parser = this.fhirContext.newJsonParser();
        this.parser.setPrettyPrint(true);

        this.libraryVersionSelector = new LibraryVersionSelector(this.adapterFactory);
        this.fhirTypeConverter = new FhirTypeConverterFactory().create(fhirContext.getVersion().getVersion());
        this.cqlFhirParametersConverter = new CqlFhirParametersConverter(this.fhirContext, this.adapterFactory, this.fhirTypeConverter);

        org.hl7.fhir.r4.model.Bundle assetBundle = AssetBundler.getContentBundle(this.fhirContext, this.getAssets());
        this.contentProvider = new BundleFhirLibraryContentProvider(this.fhirContext, assetBundle, this.adapterFactory, this.libraryVersionSelector);

        // Load terminology content, and create a TerminologyProvider which is the interface used by the evaluator for resolving terminology
        this.terminologyProvider = new BundleTerminologyProvider(this.fhirContext, assetBundle);
        this.bundleRetrieveProvider = new BundleRetrieveProvider(this.fhirContext, AssetBundler.getDataBundle(this.fhirContext, this.getAssets()));
        this.bundleRetrieveProvider.setTerminologyProvider(this.terminologyProvider);
        this.bundleRetrieveProvider.setExpandValueSets(true);

        TranslatingLibraryLoader libraryLoader = new TranslatingLibraryLoader(
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

            @Override
            protected Library getLibraryFromElm(VersionedIdentifier libraryIdentifier) {
                org.hl7.elm.r1.VersionedIdentifier versionedIdentifier = toElmIdentifier(libraryIdentifier);
                InputStream content = this.getLibraryContent(versionedIdentifier, LibraryContentType.JXSON);
                if (content != null) {
                    try {
                        return this.readJxson(content);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                return null;
            }
        };

        this.cqlEvaluator = new CqlEvaluator(libraryLoader,
                new HashMap<String, DataProvider>() {{
                    put("http://hl7.org/fhir", new CompositeDataProvider(new R4FhirModelResolver(), bundleRetrieveProvider));
                }}, this.terminologyProvider);

        this.libraryEvaluator = new LibraryEvaluator(this.cqlFhirParametersConverter, cqlEvaluator);
    }

    public void runCql(View view) {
        IBaseParameters result = libraryEvaluator.evaluate(new VersionedIdentifier().withId("ANCIND01"), Pair.of("Patient", "charity-otala-1"), null, null);

        String parameters = this.fhirContext.newJsonParser().encodeResourceToString(result);
        TextView output = this.findViewById(R.id.output_text);
        output.setText(parameters);
    }
}