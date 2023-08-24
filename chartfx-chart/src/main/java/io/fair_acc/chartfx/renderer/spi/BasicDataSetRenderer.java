package io.fair_acc.chartfx.renderer.spi;

import io.fair_acc.chartfx.marker.Marker;
import io.fair_acc.chartfx.renderer.LineStyle;
import io.fair_acc.chartfx.ui.css.CssPropertyFactory;
import io.fair_acc.chartfx.ui.css.DataSetNode;
import io.fair_acc.chartfx.ui.css.DataSetStyleParser;
import io.fair_acc.chartfx.utils.FastDoubleArrayCache;
import io.fair_acc.dataset.DataSet;
import io.fair_acc.dataset.profiler.AggregateDurationMeasure;
import io.fair_acc.dataset.profiler.Profiler;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.scene.canvas.GraphicsContext;

import static io.fair_acc.dataset.DataSet.*;

/**
 * Fast renderer for 2D lines and markers for non-error datasets. Only does basic
 * reduction and currently only supports normal poly lines.
 *
 * @author ennerf
 */
public class BasicDataSetRenderer extends AbstractRendererXY<BasicDataSetRenderer> {

    private final BooleanProperty assumeSortedData = css().createBooleanProperty(this, "assumeSortedData", true);
    private final BooleanProperty drawMarker = css().createBooleanProperty(this, "drawMarker", true);
    private final ObjectProperty<LineStyle> polyLineStyle = css().createEnumProperty(this, "polyLineStyle",
            LineStyle.NORMAL, false, LineStyle.class);

    public BasicDataSetRenderer(DataSet... dataSets) {
        getDatasets().setAll(dataSets);
    }

    @Override
    protected void render(GraphicsContext gc, DataSet dataSet, DataSetNode style) {

        // check for potentially reduced data range we are supposed to plot
        int indexMin;
        int indexMax; /* indexMax is excluded in the drawing */
        if (isAssumeSortedData()) {
            indexMin = Math.max(0, dataSet.getIndex(DataSet.DIM_X, xMin) - 1);
            indexMax = Math.min(dataSet.getIndex(DataSet.DIM_X, xMax) + 2, dataSet.getDataCount());
        } else {
            indexMin = 0;
            indexMax = dataSet.getDataCount();
        }

        final int count = indexMax - indexMin;
        if (count <= 0) {
            return;
        }

        // Store intermediate coordinates in a temporary array
        double[] xCoords = SHARED_ARRAYS.getArray(0, count);
        double[] yCoords = SHARED_ARRAYS.getArray(1, count);
        int numCoords;

        gc.save();
        gc.setLineWidth(style.getLineWidth());
        gc.setStroke(style.getLineColor());
        gc.setFill(style.getLineColor());

        // compute local screen coordinates
        for (int i = indexMin; i < indexMax; ) {

            benchComputeCoords.start();

            // find the first valid point
            double xi = dataSet.get(DIM_X, i);
            double yi = dataSet.get(DIM_Y, i);
            i++;
            while (Double.isNaN(xi) || Double.isNaN(yi)) {
                i++;
                continue;
            }

            // start coord array
            double prevX = xCoords[0] = xAxis.getDisplayPosition(xi);
            double prevY = yCoords[0] = yAxis.getDisplayPosition(yi);
            numCoords = 1;

            // Build contiguous non-nan segments, so we can use the more efficient strokePolyLine
            while (i < indexMax) {
                xi = dataSet.get(DIM_X, i);
                yi = dataSet.get(DIM_Y, i);
                i++;

                // Skip iteration and draw whatever we have for now
                if (Double.isNaN(xi) || Double.isNaN(yi)) {
                    break;
                }

                // Remove points that are unnecessary
                final double x = xAxis.getDisplayPosition(xi);
                final double y = yAxis.getDisplayPosition(yi);
                if (isSamePoint(prevX, prevY, x, y)) {
                    continue;
                }

                // Add point
                xCoords[numCoords] = prevX = x;
                yCoords[numCoords] = prevY = y;
                numCoords++;

            }
            benchComputeCoords.stop();

            // Draw elements
            drawMarkers(gc, style, xCoords, yCoords, numCoords);
            drawPolyLine(gc, style, xCoords, yCoords, numCoords);

        }

        // Overwrite special data points (draws on top of the other)
        drawCustomStyledMarkers(gc, style, dataSet, indexMin, indexMax);

        gc.restore();
        benchComputeCoords.reportSum();
        benchDrawMarker.reportSum();
        benchPolyLine.reportSum();

    }

    protected void drawMarkers(GraphicsContext gc, DataSetNode style, double[] x, double[] y, int length) {
        var markerSize = style.getMarkerSize();
        if (!isDrawMarker() || markerSize == 0) {
            return;
        }
        var marker = style.getMarkerType();
        benchDrawMarker.start();
        for (int i = 0; i < length; i++) {
            marker.draw(gc, x[i], y[i], markerSize);
        }
        benchDrawMarker.stop();
    }

    protected void drawPolyLine(GraphicsContext gc, DataSetNode style, double[] x, double[] y, int length) {
        if (getPolyLineStyle() == LineStyle.NONE || style.getLineWidth() == 0) {
            return;
        }
        benchPolyLine.start();
        if (length > 1) {
            // solid and dashed line
            gc.strokePolyline(x, y, length);
        } else {
            // corner case for a single point that would be skipped by strokePolyLine
            gc.strokeLine(x[0], y[0], x[0], y[0]);
        }
        benchPolyLine.stop();
    }

    protected void drawCustomStyledMarkers(GraphicsContext gc, DataSetNode style, DataSet dataSet, int min, int max) {
        if (!isDrawMarker() || !dataSet.hasStyles()) {
            return;
        }
        dataSet.forEachStyle(min, max, (index, string) -> {
            if (!styleParser.tryParse(string)) {
                return;
            }
            var size = styleParser.getMarkerSize().orElse(style.getMarkerSize());
            if (size == 0) {
                return;
            }
            double x = xAxis.getDisplayPosition(dataSet.get(DIM_X, index));
            double y = yAxis.getDisplayPosition(dataSet.get(DIM_Y, index));
            var customMarker = styleParser.getMarkerType().orElse(style.getMarkerType());
            var color = styleParser.getMarkerColor().orElse(style.getMarkerColor());
            gc.save();
            gc.setFill(color);
            gc.setStroke(color);
            gc.setLineDashes(styleParser.getMarkerLineDashes().orElse(style.getMarkerLineDashes()));
            gc.setLineWidth(styleParser.getMarkerLineWidth().orElse(style.getMarkerLineWidth()));
            customMarker.draw(gc, x, y, size);
            gc.restore();
        });
    }

    public boolean isAssumeSortedData() {
        return assumeSortedData.get();
    }

    public BooleanProperty assumeSortedDataProperty() {
        return assumeSortedData;
    }

    public void setAssumeSortedData(boolean assumeSortedData) {
        this.assumeSortedData.set(assumeSortedData);
    }

    public boolean isDrawMarker() {
        return drawMarker.get();
    }

    public BooleanProperty drawMarkerProperty() {
        return drawMarker;
    }

    public void setDrawMarker(boolean drawMarker) {
        this.drawMarker.set(drawMarker);
    }

    public LineStyle getPolyLineStyle() {
        return polyLineStyle.get();
    }

    public ObjectProperty<LineStyle> polyLineStyleProperty() {
        return polyLineStyle;
    }

    public void setPolyLineStyle(LineStyle polyLineStyle) {
        this.polyLineStyle.set(polyLineStyle);
    }

    public static boolean isSamePoint(double prevX, double prevY, double x, double y) {
        // Keep points within a certain pixel distance
        double dx = Math.abs(x - prevX);
        double dy = Math.abs(y - prevY);
        return dx < minPointPixelDistance && dy < minPointPixelDistance;
    }

    // 1 Pixel distance already filters out quite a bit. Anything higher looks jumpy.
    private static final double minPointPixelDistance = 1;

    @Override
    protected BasicDataSetRenderer getThis() {
        return this;
    }

    @Override
    public void setProfiler(Profiler profiler) {
        super.setProfiler(profiler);
        benchComputeCoords = AggregateDurationMeasure.wrap(profiler.newDebugDuration("basic-computeCoords"));
        benchDrawMarker = AggregateDurationMeasure.wrap(profiler.newDebugDuration("basic-drawMarker"));
        benchPolyLine = AggregateDurationMeasure.wrap(profiler.newDebugDuration("basic-drawPolyLine"));
    }

    AggregateDurationMeasure benchComputeCoords = AggregateDurationMeasure.DISABLED;
    AggregateDurationMeasure benchDrawMarker = AggregateDurationMeasure.DISABLED;
    AggregateDurationMeasure benchPolyLine = AggregateDurationMeasure.DISABLED;

    private final DataSetStyleParser styleParser = DataSetStyleParser.newInstance();

    @Override
    protected CssPropertyFactory<AbstractRenderer<?>> css() {
        return CSS;
    }

    private static final CssPropertyFactory<AbstractRenderer<?>> CSS = new CssPropertyFactory<>(AbstractPointReducingRenderer.getClassCssMetaData());

    private static final FastDoubleArrayCache SHARED_ARRAYS = new FastDoubleArrayCache(2);

    /**
     * Deletes all arrays that are larger than necessary for the last drawn dataset
     */
    public static void trimCache() {
        SHARED_ARRAYS.trim();
    }

}
