package sa.id.BioQA.service.semanticSimilarity.unsupervisedMethod;

import com.google.common.io.Resources;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.datavec.api.util.ClassPathResource;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.simmetrics.StringMetric;
import org.simmetrics.metrics.StringMetrics;
import sa.id.BioQA.BioQaApplication;
import sa.id.BioQA.service.semanticSimilarity.core.SimilarityMeasure;

import java.io.*;

public class SentenceVectorsBasedSimilarity implements SimilarityMeasure {

    static final Logger logger = LogManager.getLogger(SentenceVectorsBasedSimilarity.class.getName());
    static final String
            PARAGRAPHVECTORMODELPATH = BioQaApplication.class.getResource("/public/paragraphVectors/paragVecModel.zip").getFile();
    static final ParagraphVectors PARAGRAPHVECS = loadParagraphVectors();

    private static ParagraphVectors loadParagraphVectors() {
        ParagraphVectors paragraphVectors = null;
        try {
            paragraphVectors = WordVectorSerializer.readParagraphVectors(PARAGRAPHVECTORMODELPATH);
            TokenizerFactory t = new DefaultTokenizerFactory();
            t.setTokenPreProcessor(new CommonPreprocessor());
            paragraphVectors.setTokenizerFactory(t);
            paragraphVectors.getConfiguration().setIterations(10); // please note, we set iterations to 1 here, just to speedup inference

        } catch (IOException e) {
            e.printStackTrace();
        }
        return paragraphVectors;
    }

    public static void main(String[] args) throws IOException {

        SentenceVectorsBasedSimilarity d = new SentenceVectorsBasedSimilarity();
   //     d.readTrainingDataAndPrepareIt();
        d.trainParagraghVecModel(PARAGRAPHVECTORMODELPATH);
    //    d.produceParagraphVectorOfGivenSentence("i love you");


    }

    private void readTrainingDataAndPrepareIt() throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("train-file.txt")));
        String line;
        int count = 0;
        while ((line = bufferedReader.readLine())!=null){
            System.out.println(line);
            count++;
            if(count == 100)
                break;
        }
    }

    public INDArray produceParagraphVectorOfGivenSentence(String testSentence) {
        INDArray inferredVectorA = null;
        if (PARAGRAPHVECS != null) {
            inferredVectorA = PARAGRAPHVECS.inferVector(testSentence.toLowerCase().trim());
            return inferredVectorA;
        }
        return inferredVectorA;
    }

    public void trainParagraghVecModel(String locationToSave) throws FileNotFoundException {
        ClassPathResource resource = new ClassPathResource("/paragraphVectors/paragraphVectorTraining.txt");
        File file = resource.getFile();
        SentenceIterator iter = new BasicLineIterator(file);
        AbstractCache<VocabWord> cache = new AbstractCache<VocabWord>();
        TokenizerFactory t = new DefaultTokenizerFactory();
        t.setTokenPreProcessor(new CommonPreprocessor());
        /*
             if you don't have LabelAwareIterator handy, you can use synchronized labels generator
              it will be used to label each document/sequence/line with it's own label.
              But if you have LabelAwareIterator ready, you can can provide it, for your in-house labels
        */
        LabelsSource source = new LabelsSource("DOC_");

        ParagraphVectors vec = new ParagraphVectors.Builder()
                .minWordFrequency(1)
                .iterations(100)
                .epochs(1)
                .layerSize(50)
                .learningRate(0.02)
                .labelsSource(source)
                .windowSize(5)
                .iterate(iter)
                .trainWordVectors(true)
                .vocabCache(cache)
                .tokenizerFactory(t)
                .sampling(0)
                .build();

        vec.fit();

        WordVectorSerializer.writeParagraphVectors(vec, locationToSave);
    }

    public double getSimilarity(String sentence1, String sentence2){
        double predictedScore = 0;
        if (PARAGRAPHVECS != null) {
            try {
                INDArray inferredVectorA = produceParagraphVectorOfGivenSentence(sentence1);
                INDArray inferredVectorB = produceParagraphVectorOfGivenSentence(sentence2);
                predictedScore = Transforms.cosineSim(inferredVectorA, inferredVectorB);
            } catch (Exception e) {
                logger.error("No word is matched with the given sentence and any sentence in training set - model file. " + sentence1
                        + ";" + sentence2);
                System.out.println("No word is matched with the given sentence and any sentence in training set - model file. " + sentence1
                        + ";" + sentence2);
                StringMetric metric = StringMetrics.qGramsDistance();
                predictedScore = metric.compare(sentence1, sentence2);
            }
        }

        return predictedScore;
    }
}
