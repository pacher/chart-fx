package io.fair_acc.dataset.spi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.fair_acc.dataset.DataSet;
import io.fair_acc.dataset.GridDataSet;
import io.fair_acc.dataset.events.ChartBits;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for the DimReductionDataSet
 * -- Reduces 3D data to 2D DataSet either via slicing, min, mean, max or integration
 *
 * @author rstein
 */
public class DimReductionDataSetTests {
    private final AtomicInteger nEvent = new AtomicInteger(0);

    @Test
    public void testGetterSetterConsistency() {
        GridDataSet testData = new DataSetBuilder("test") //
                                       .setValuesNoCopy(DataSet.DIM_X, new double[] { 1, 2, 3 }) // x-array
                                       .setValuesNoCopy(DataSet.DIM_Y, new double[] { 6, 7, 8 }) // y-array
                                       .setValues(DataSet.DIM_Z, new double[][] { // z-array
                                                                 new double[] { 1, 2, 3 }, //
                                                                 new double[] { 6, 5, 4 }, //
                                                                 new double[] { 9, 8, 7 } }) //
                                       .build(GridDataSet.class);

        DimReductionDataSet reducedDataSetX = new DimReductionDataSet(testData, DataSet.DIM_X, DimReductionDataSet.Option.INTEGRAL);
        DimReductionDataSet reducedDataSetY = new DimReductionDataSet(testData, DataSet.DIM_Y, DimReductionDataSet.Option.INTEGRAL);

        reducedDataSetX.setMinValue(0.0);
        assertEquals(0.0, reducedDataSetX.getMinValue());
        reducedDataSetX.setMinValue(2.0);
        assertEquals(2.0, reducedDataSetX.getMinValue());

        reducedDataSetX.setMaxValue(0.0);
        assertEquals(0.0, reducedDataSetX.getMaxValue());
        reducedDataSetX.setMaxValue(2.0);
        assertEquals(2.0, reducedDataSetX.getMaxValue());

        reducedDataSetX.setRange(1.5, 2.5);
        assertEquals(1.5, reducedDataSetX.getMinValue());
        assertEquals(2.5, reducedDataSetX.getMaxValue());

        reducedDataSetY.setMinValue(0.0);
        assertEquals(0.0, reducedDataSetY.getMinValue());
        reducedDataSetY.setMinValue(2.0);
        assertEquals(2.0, reducedDataSetY.getMinValue());

        reducedDataSetY.setMaxValue(0.0);
        assertEquals(0.0, reducedDataSetY.getMaxValue());
        reducedDataSetY.setMaxValue(2.0);
        assertEquals(2.0, reducedDataSetY.getMaxValue());

        reducedDataSetY.setRange(1.5, 2.5);
        assertEquals(1.5, reducedDataSetY.getMinValue());
        assertEquals(2.5, reducedDataSetY.getMaxValue());
    }

    @Test
    public void testIntegralOptions() {
        GridDataSet testData = new DataSetBuilder("test") //
                                       .setValuesNoCopy(DataSet.DIM_X, new double[] { 1, 2, 3 }) // x-array
                                       .setValuesNoCopy(DataSet.DIM_Y, new double[] { 6, 7, 8 }) // y-array
                                       .setValues(DataSet.DIM_Z, new double[][] { // z-array
                                                                 new double[] { 1, 2, 3 }, //
                                                                 new double[] { 6, 5, 4 }, //
                                                                 new double[] { 9, 8, 7 } }) //
                                       .build(GridDataSet.class);

        DimReductionDataSet sliceDataSetX = new DimReductionDataSet(testData, DataSet.DIM_X, DimReductionDataSet.Option.INTEGRAL);
        DimReductionDataSet sliceDataSetY = new DimReductionDataSet(testData, DataSet.DIM_Y, DimReductionDataSet.Option.INTEGRAL);

        Assertions.assertEquals(testData, sliceDataSetX.getSourceDataSet(), "equal source dataSet");
        Assertions.assertEquals(testData, sliceDataSetY.getSourceDataSet(), "equal source dataSet");
        Assertions.assertEquals(DimReductionDataSet.Option.INTEGRAL, sliceDataSetY.getReductionOption(), "reduction option");

        sliceDataSetY.setMinValue(0);
        assertEquals(0, sliceDataSetY.getMinIndex());
        sliceDataSetY.setMinValue(2);
        assertEquals(1, sliceDataSetY.getMinIndex());
        sliceDataSetY.setMinValue(0);
        assertEquals(0, sliceDataSetY.getMinIndex());

        sliceDataSetY.setMaxValue(2);
        assertEquals(1, sliceDataSetY.getMaxIndex());
        sliceDataSetY.setMaxValue(3);
        assertEquals(2, sliceDataSetY.getMaxIndex());

        sliceDataSetX.setMinValue(5);
        assertEquals(0, sliceDataSetX.getMinIndex());
        sliceDataSetX.setMinValue(6);
        assertEquals(0, sliceDataSetX.getMinIndex());
        sliceDataSetX.setMinValue(7);
        assertEquals(1, sliceDataSetX.getMinIndex());
        sliceDataSetX.setMinValue(0);

        sliceDataSetX.setMaxValue(7);
        assertEquals(1, sliceDataSetX.getMaxIndex());
        sliceDataSetX.setMaxValue(8);
        assertEquals(2, sliceDataSetX.getMaxIndex());

        // integral over full array
        final double[] integralX = new double[3];
        final double[] integralY = new double[3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                integralX[j] += testData.get(DataSet.DIM_Z, j, i);
                integralY[i] += testData.get(DataSet.DIM_Z, j, i);
            }
        }
        sliceDataSetY.setMinValue(0);
        sliceDataSetY.setMaxValue(4);
        sliceDataSetX.setMinValue(0);
        sliceDataSetX.setMaxValue(9);

        assertArrayEquals(integralX, sliceDataSetX.getValues(DataSet.DIM_Y), "x-integral");
        assertArrayEquals(integralY, sliceDataSetY.getValues(DataSet.DIM_Y), "y-integral");
    }

    @Test
    public void testMaxOptions() {
        GridDataSet testData = new DataSetBuilder("test") //
                                       .setValuesNoCopy(DataSet.DIM_X, new double[] { 1, 2, 3 }) // x-array
                                       .setValuesNoCopy(DataSet.DIM_Y, new double[] { 6, 7, 8 }) // y-array
                                       .setValues(DataSet.DIM_Z, new double[][] { // z-array
                                                                 new double[] { 1, 2, 3 }, //
                                                                 new double[] { 6, 5, 4 }, //
                                                                 new double[] { 9, 8, 7 } }) //
                                       .build(GridDataSet.class);

        DimReductionDataSet sliceDataSetX = new DimReductionDataSet(testData, DataSet.DIM_X, DimReductionDataSet.Option.MAX);
        DimReductionDataSet sliceDataSetY = new DimReductionDataSet(testData, DataSet.DIM_Y, DimReductionDataSet.Option.MAX);

        Assertions.assertEquals(testData, sliceDataSetX.getSourceDataSet(), "equal source dataSet");
        Assertions.assertEquals(testData, sliceDataSetY.getSourceDataSet(), "equal source dataSet");
        Assertions.assertEquals(DimReductionDataSet.Option.MAX, sliceDataSetX.getReductionOption(), "reduction option");

        // max over full array
        final double[] maxY = new double[3];
        final double[] maxX = new double[3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                maxX[j] = Math.max(testData.get(DataSet.DIM_Z, j, i), maxX[j]);
                maxY[i] = Math.max(testData.get(DataSet.DIM_Z, j, i), maxY[i]);
            }
        }

        sliceDataSetX.setMinValue(0);
        sliceDataSetX.setMaxValue(10);
        sliceDataSetY.setMinValue(0);
        sliceDataSetY.setMaxValue(4);

        assertArrayEquals(maxX, sliceDataSetX.getValues(DataSet.DIM_Y), "x-max");
        assertArrayEquals(maxY, sliceDataSetY.getValues(DataSet.DIM_Y), "y-max");
    }

    @Test
    public void testMeanOptions() {
        GridDataSet testData = new DataSetBuilder("test") //
                                       .setValuesNoCopy(DataSet.DIM_X, new double[] { 1, 2, 3 }) // x-array
                                       .setValuesNoCopy(DataSet.DIM_Y, new double[] { 6, 7, 8 }) // y-array
                                       .setValues(DataSet.DIM_Z, new double[][] { // z-array
                                                                 new double[] { 1, 2, 3 }, //
                                                                 new double[] { 6, 5, 4 }, //
                                                                 new double[] { 9, 8, 7 } }) //
                                       .build(GridDataSet.class);

        DimReductionDataSet sliceDataSetX = new DimReductionDataSet(testData, DataSet.DIM_X, DimReductionDataSet.Option.MEAN);
        DimReductionDataSet sliceDataSetY = new DimReductionDataSet(testData, DataSet.DIM_Y, DimReductionDataSet.Option.MEAN);

        Assertions.assertEquals(testData, sliceDataSetX.getSourceDataSet(), "equal source dataSet");
        Assertions.assertEquals(testData, sliceDataSetY.getSourceDataSet(), "equal source dataSet");
        Assertions.assertEquals(DimReductionDataSet.Option.MEAN, sliceDataSetX.getReductionOption(), "reduction option");

        // mean over full array
        final double[] meanX = new double[3];
        final double[] meanY = new double[3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                meanX[j] += testData.get(DataSet.DIM_Z, j, i) / 3.0;
                meanY[i] += testData.get(DataSet.DIM_Z, j, i) / 3.0;
            }
        }

        sliceDataSetX.setMinValue(0);
        sliceDataSetX.setMaxValue(10);
        sliceDataSetY.setMinValue(0);
        sliceDataSetY.setMaxValue(4);

        assertEquals(0, sliceDataSetX.getMinIndex());
        assertEquals(2, sliceDataSetX.getMaxIndex());
        assertEquals(0, sliceDataSetY.getMinIndex());
        assertEquals(2, sliceDataSetY.getMaxIndex());

        for (int i = 0; i < 3; i++) {
            assertEquals(meanX[i], sliceDataSetX.get(DataSet.DIM_Y, i), 1e-14, "x-integral " + i);
            assertEquals(meanY[i], sliceDataSetY.get(DataSet.DIM_Y, i), 1e-14, "y-integral " + i);
        }
    }

    @Test
    public void testMinOptions() {
        GridDataSet testData = new DataSetBuilder("test") //
                                       .setValuesNoCopy(DataSet.DIM_X, new double[] { 1, 2, 3 }) // x-array
                                       .setValuesNoCopy(DataSet.DIM_Y, new double[] { 6, 7, 8 }) // y-array
                                       .setValues(DataSet.DIM_Z, new double[][] { // z-array
                                                                 new double[] { 1, 2, 3 }, //
                                                                 new double[] { 6, 5, 4 }, //
                                                                 new double[] { 9, 8, 7 } }) //
                                       .build(GridDataSet.class);

        DimReductionDataSet sliceDataSetX = new DimReductionDataSet(testData, DataSet.DIM_X, DimReductionDataSet.Option.MIN);
        DimReductionDataSet sliceDataSetY = new DimReductionDataSet(testData, DataSet.DIM_Y, DimReductionDataSet.Option.MIN);

        Assertions.assertEquals(testData, sliceDataSetX.getSourceDataSet(), "equal source dataSet");
        Assertions.assertEquals(testData, sliceDataSetY.getSourceDataSet(), "equal source dataSet");
        Assertions.assertEquals(DimReductionDataSet.Option.MIN, sliceDataSetX.getReductionOption(), "reduction option");

        // min over full array
        final double[] minY = new double[] { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
        final double[] minX = new double[] { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                minX[j] = Math.min(testData.get(DataSet.DIM_Z, j, i), minX[j]);
                minY[i] = Math.min(testData.get(DataSet.DIM_Z, j, i), minY[i]);
            }
        }

        sliceDataSetX.setMinValue(0);
        sliceDataSetX.setMaxValue(10);
        sliceDataSetY.setMinValue(0);
        sliceDataSetY.setMaxValue(4);

        assertArrayEquals(minX, sliceDataSetX.getValues(DataSet.DIM_Y), "x-min");
        assertArrayEquals(minY, sliceDataSetY.getValues(DataSet.DIM_Y), "y-min");
    }

    @Disabled // TODO: update does not get triggered automatically. check after refactoring dependent data sets
    @Test
    public void testSliceOptions() {
        GridDataSet testData = new DataSetBuilder("test") //
                                       .setValuesNoCopy(DataSet.DIM_X, new double[] { 1, 2, 3 }) // x-array
                                       .setValuesNoCopy(DataSet.DIM_Y, new double[] { 6, 7, 8 }) // y-array
                                       .setValues(DataSet.DIM_Z, new double[][] { // z-array
                                                                 new double[] { 1, 2, 3 }, //
                                                                 new double[] { 6, 5, 4 }, //
                                                                 new double[] { 9, 8, 7 } }) //
                                       .build(GridDataSet.class);

        DimReductionDataSet sliceDataSetX = new DimReductionDataSet(testData, DataSet.DIM_X, DimReductionDataSet.Option.SLICE);
        DimReductionDataSet sliceDataSetY = new DimReductionDataSet(testData, DataSet.DIM_Y, DimReductionDataSet.Option.SLICE);

        Assertions.assertEquals(testData, sliceDataSetX.getSourceDataSet(), "equal source dataSet");
        Assertions.assertEquals(testData, sliceDataSetY.getSourceDataSet(), "equal source dataSet");
        Assertions.assertEquals(DimReductionDataSet.Option.SLICE, sliceDataSetX.getReductionOption(), "reduction option");

        nEvent.set(0);
        sliceDataSetX.getBitState().addInvalidateListener(ChartBits.DataSetDataAdded, (src, bits) -> {
            nEvent.incrementAndGet();
        });
        testData.fireInvalidated(ChartBits.DataSetData);
        Awaitility.await().atMost(1, TimeUnit.SECONDS).alias("DataSet3D event propagated").until(() -> nEvent.get() == 1);

        assertArrayEquals(testData.getGridValues(DataSet.DIM_X), sliceDataSetX.getValues(DataSet.DIM_X));
        assertArrayEquals(testData.getGridValues(DataSet.DIM_Y), sliceDataSetY.getValues(DataSet.DIM_X));

        assertArrayEquals(new double[] { 1, 2, 3 }, sliceDataSetX.getValues(DataSet.DIM_Y), "first row match");
        assertArrayEquals(testData.getGridValues(DataSet.DIM_Y), sliceDataSetY.getValues(DataSet.DIM_X));

        sliceDataSetX.setMinValue(7.0);
        Awaitility.await().atMost(1, TimeUnit.SECONDS).alias("DataSet3D event propagated").until(() -> nEvent.get() == 2);

        assertArrayEquals(new double[] { 6, 5, 4 }, sliceDataSetX.getValues(DataSet.DIM_Y), "second row match");
        assertArrayEquals(testData.getGridValues(DataSet.DIM_Y), sliceDataSetY.getValues(DataSet.DIM_X));

        assertArrayEquals(new double[] { 1, 6, 9 }, sliceDataSetY.getValues(DataSet.DIM_Y), "first column match");
        sliceDataSetY.setMinValue(2.0);
        assertArrayEquals(new double[] { 2, 5, 8 }, sliceDataSetY.getValues(DataSet.DIM_Y), "second column match");
    }

    @Disabled // TODO: update does not get triggered automatically. check after refactoring dependent data sets
    @Test
    public void testInvalid2DInputDataSet() {
        GridDataSet testData = new DoubleGridDataSet("test", false, new double[][] { { 1, 2, 3 } }, new double[] { 6, 7, 8 });

        DimReductionDataSet sliceDataSetX = new DimReductionDataSet(testData, DataSet.DIM_X, DimReductionDataSet.Option.SLICE);
        testData.fireInvalidated(ChartBits.DataSetData);
        assertEquals("input data set not 3 dim grid data set", sliceDataSetX.getWarningList().get(0));
    }
}
