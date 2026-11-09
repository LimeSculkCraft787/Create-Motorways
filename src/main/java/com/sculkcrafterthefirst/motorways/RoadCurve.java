package com.sculkcrafterthefirst.motorways;

public class RoadCurve {
    public final int count;
    public final float[] x;
    public final float[] y;
    public final float[] z;
    public final float[] tx;
    public final float[] tz;
    public final double length;
    public final double minRadius;

    public RoadCurve(double dx, double dy, double dz, double sdx, double sdz, double edx, double edz) {
        double chord = Math.sqrt(dx * dx + dz * dz);
        double cx = chord > 1e-6 ? dx / chord : 1.0;
        double cz = chord > 1e-6 ? dz / chord : 0.0;

        double sl = Math.sqrt(sdx * sdx + sdz * sdz);
        if (sl < 1e-6) {
            sdx = cx;
            sdz = cz;
        } else {
            sdx /= sl;
            sdz /= sl;
        }
        double el = Math.sqrt(edx * edx + edz * edz);
        if (el < 1e-6) {
            edx = cx;
            edz = cz;
        } else {
            edx /= el;
            edz /= el;
        }

        double k = chord * 0.4;
        double p1x = sdx * k;
        double p1z = sdz * k;
        double p2x = dx - edx * k;
        double p2z = dz - edz * k;

        int m = 256;
        double[] cum = new double[m + 1];
        double prevX = 0;
        double prevZ = 0;
        double prevAngle = Math.atan2(sdz, sdx);
        double maxCurv = 0;
        for (int i = 1; i <= m; i++) {
            double t = (double) i / m;
            double bx = bezier(0, p1x, p2x, dx, t);
            double bz = bezier(0, p1z, p2z, dz, t);
            double seg = Math.hypot(bx - prevX, bz - prevZ);
            cum[i] = cum[i - 1] + seg;
            double vx = derivative(0, p1x, p2x, dx, t);
            double vz = derivative(0, p1z, p2z, dz, t);
            if (Math.hypot(vx, vz) > 1e-9) {
                double ang = Math.atan2(vz, vx);
                double da = ang - prevAngle;
                while (da > Math.PI) {
                    da -= 2 * Math.PI;
                }
                while (da < -Math.PI) {
                    da += 2 * Math.PI;
                }
                if (seg > 1e-6) {
                    maxCurv = Math.max(maxCurv, Math.abs(da) / seg);
                }
                prevAngle = ang;
            }
            prevX = bx;
            prevZ = bz;
        }
        double len = cum[m];
        this.length = len;
        this.minRadius = maxCurv > 1e-9 ? 1.0 / maxCurv : Double.MAX_VALUE;

        int n = Math.max(1, (int) Math.round(len));
        float[] xs = new float[n + 1];
        float[] ys = new float[n + 1];
        float[] zs = new float[n + 1];
        float[] txs = new float[n + 1];
        float[] tzs = new float[n + 1];

        int idx = 0;
        for (int j = 0; j <= n; j++) {
            double s = len * j / n;
            while (idx < m - 1 && cum[idx + 1] < s) {
                idx++;
            }
            double span = cum[idx + 1] - cum[idx];
            double f = span > 1e-9 ? (s - cum[idx]) / span : 0.0;
            double t = Math.min(1.0, (idx + f) / m);

            double bx = bezier(0, p1x, p2x, dx, t);
            double bz = bezier(0, p1z, p2z, dz, t);
            double vx = derivative(0, p1x, p2x, dx, t);
            double vz = derivative(0, p1z, p2z, dz, t);
            double vl = Math.hypot(vx, vz);
            if (vl < 1e-9) {
                vx = cx;
                vz = cz;
                vl = 1.0;
            }
            double u = len > 1e-9 ? s / len : 0.0;
            double smooth = u * u * (3 - 2 * u);

            xs[j] = (float) (0.5 + bx);
            zs[j] = (float) (0.5 + bz);
            ys[j] = (float) (dy * smooth);
            txs[j] = (float) (vx / vl);
            tzs[j] = (float) (vz / vl);
        }

        this.count = n + 1;
        this.x = xs;
        this.y = ys;
        this.z = zs;
        this.tx = txs;
        this.tz = tzs;
    }

    private static double bezier(double p0, double p1, double p2, double p3, double t) {
        double u = 1 - t;
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
    }

    private static double derivative(double p0, double p1, double p2, double p3, double t) {
        double u = 1 - t;
        return 3 * (u * u * (p1 - p0) + 2 * u * t * (p2 - p1) + t * t * (p3 - p2));
    }
}
