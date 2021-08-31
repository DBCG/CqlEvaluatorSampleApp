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
import org.hl7.fhir.r4.model.MeasureReport;
import org.opencds.cqf.cql.engine.data.CompositeDataProvider;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.fhir.converter.FhirTypeConverter;
import org.opencds.cqf.cql.engine.fhir.converter.FhirTypeConverterFactory;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.cql.engine.model.ModelResolver;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.opencds.cqf.cql.evaluator.CqlEvaluator;
import org.opencds.cqf.cql.evaluator.builder.Constants;
import org.opencds.cqf.cql.evaluator.builder.DataProviderFactory;
import org.opencds.cqf.cql.evaluator.builder.EndpointConverter;
import org.opencds.cqf.cql.evaluator.builder.FhirDalFactory;
import org.opencds.cqf.cql.evaluator.builder.LibraryContentProviderFactory;
import org.opencds.cqf.cql.evaluator.builder.ModelResolverFactory;
import org.opencds.cqf.cql.evaluator.builder.TerminologyProviderFactory;
import org.opencds.cqf.cql.evaluator.builder.dal.TypedFhirDalFactory;
import org.opencds.cqf.cql.evaluator.builder.data.FhirModelResolverFactory;
import org.opencds.cqf.cql.evaluator.builder.data.TypedRetrieveProviderFactory;
import org.opencds.cqf.cql.evaluator.builder.library.TypedLibraryContentProviderFactory;
import org.opencds.cqf.cql.evaluator.builder.terminology.TypedTerminologyProviderFactory;
import org.opencds.cqf.cql.evaluator.cql2elm.content.LibraryContentProvider;
import org.opencds.cqf.cql.evaluator.cql2elm.content.fhir.BundleFhirLibraryContentProvider;
import org.opencds.cqf.cql.evaluator.cql2elm.util.LibraryVersionSelector;
import org.opencds.cqf.cql.evaluator.engine.execution.TranslatingLibraryLoader;
import org.opencds.cqf.cql.evaluator.engine.model.CachingModelResolverDecorator;
import org.opencds.cqf.cql.evaluator.engine.retrieve.BundleRetrieveProvider;
import org.opencds.cqf.cql.evaluator.engine.terminology.BundleTerminologyProvider;
import org.opencds.cqf.cql.evaluator.fhir.adapter.r4.AdapterFactory;
import org.opencds.cqf.cql.evaluator.fhir.dal.BundleFhirDal;
import org.opencds.cqf.cql.evaluator.fhir.dal.FhirDal;
import org.opencds.cqf.cql.evaluator.library.CqlFhirParametersConverter;
import org.opencds.cqf.cql.evaluator.library.LibraryEvaluator;
import org.opencds.cqf.cql.evaluator.measure.MeasureEvalConfig;
import org.opencds.cqf.cql.evaluator.measure.r4.R4MeasureProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.api.BundleInclusionRule;
import ca.uhn.fhir.model.valueset.BundleTypeEnum;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.BundleLinks;
import ca.uhn.fhir.rest.api.IVersionSpecificBundleFactory;

public class MeasureActivity extends AppCompatActivity {

    FhirContext fhirContext = FhirContext.forCached(FhirVersionEnum.R4);
    R4MeasureProcessor measureProcessor;

    // This was stolen from the test cases in the CQL Evaluator... Need to consolidate the API into IFhirPlatform.
    protected void setup() {
        // TODO: Mockito a good solid chunk of this setup...

        AdapterFactory adapterFactory = new org.opencds.cqf.cql.evaluator.fhir.adapter.r4.AdapterFactory();

        LibraryVersionSelector libraryVersionSelector = new LibraryVersionSelector(adapterFactory);
        LibraryContentProvider libraryContentProvider = new BundleFhirLibraryContentProvider(fhirContext,
                AssetBundler.getContentBundle(fhirContext, getAssets()),
                adapterFactory, libraryVersionSelector);
        TerminologyProvider terminologyProvider =  new BundleTerminologyProvider(fhirContext, AssetBundler.getContentBundle(fhirContext, getAssets()));
        BundleRetrieveProvider bundleRetrieveProvider = new BundleRetrieveProvider(this.fhirContext, AssetBundler.getDataBundle(this.fhirContext, this.getAssets()));
        bundleRetrieveProvider.setTerminologyProvider(terminologyProvider);
        bundleRetrieveProvider.setExpandValueSets(true);


        DataProvider dataProvider = new CompositeDataProvider(new CachingModelResolverDecorator(new R4FhirModelResolver()), bundleRetrieveProvider);

        BundleFhirDal fhirDal =   new BundleFhirDal(fhirContext, AssetBundler.getContentBundle(fhirContext, getAssets()));
        EndpointConverter endpointConverter = new EndpointConverter(adapterFactory);

        MeasureEvalConfig config = MeasureEvalConfig.defaultConfig();
//        config.setMeasureEvalOptions(EnumSet.of(MeasureEvalOptions.ENABLE_DEBUG_LOGGING));

        this.measureProcessor = new R4MeasureProcessor(null, null,
               null, null, null, terminologyProvider, libraryContentProvider, dataProvider, fhirDal, config);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measure);
        this.setup();;
    }
    public void runCql(View view) {
        MeasureReport result = measureProcessor.evaluateMeasure("http://fhir.org/guides/who/anc-cds/Measure/ANCIND01", "2020-01-01", "2020-01-31", "subject", "patient-charity-otala-1", null, null, null, null, null, null);

        String parameters = this.fhirContext.newJsonParser().encodeResourceToString(result);
        TextView output = this.findViewById(R.id.output_text);
        output.setText(parameters);
    }
}