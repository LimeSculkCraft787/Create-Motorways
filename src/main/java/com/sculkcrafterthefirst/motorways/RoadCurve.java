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
        this(dx, dy, dz, sdx, sdz, edx, edz, 0, 0);
    }

    // so / eo: seitlicher Versatz am Start / Ende (für Spuranschlüsse)
    public RoadCurve(double dx, double dy, double dz, double sdx, double sdz, double edx, double edz,
                     double so, double eo) {
        double chord0 = Math.sqrt(dx * dx + dz * dz);
        double cx0 = chord0 > 1e-6 ? dx / chord0 : 1.0;
        double cz0 = chord0 > 1e-6 ? dz / chord0 : 0.0;

        double sl = Math.sqrt(sdx * sdx + sdz * sdz);
        if (sl < 1e-6) {
            sdx = cx0;
            sdz = cz0;
        } else {
            sdx /= sl;
            sdz /= sl;
        }
        double el = Math.sqrt(edx * edx + edz * edz);
        if (el < 1e-6) {
            edx = cx0;
            edz = cz0;
        } else {
            edx /= el;
            edz /= el;
        }

        double p0x = -sdz * so;
        double p0z = sdx * so;
        double p3x = dx - edz * eo;
        double p3z = dz + edx * eo;
        double qx = p3x - p0x;
        double qz = p3z - p0z;
        double chord = Math.sqrt(qx * qx + qz * qz);
        double cx = chord > 1e-6 ? qx / chord : cx0;
        double cz = chord > 1e-6 ? qz / chord : cz0;

        double k = chord * 0.4;
        double p1x = p0x + sdx * k;
        double p1z = p0z + sdz * k;
        double p2x = p3x - edx * k;
        double p2z = p3z - edz * k;

        int m = 256;
        double[] cum = new double[m + 1];
        double prevX = p0x;
        double prevZ = p0z;
        double prevAngle = Math.atan2(sdz, sdx);
        double maxCurv = 0;
        for (int i = 1; i <= m; i++) {
            double t = (double) i / m;
            double bx = bezier(p0x, p1x, p2x, p3x, t);
            double bz = bezier(p0z, p1z, p2z, p3z, t);
            double seg = Math.hypot(bx - prevX, bz - prevZ);
            cum[i] = cum[i - 1] + seg;
            double vx = derivative(p0x, p1x, p2x, p3x, t);
            double vz = derivative(p0z, p1z, p2z, p3z, t);
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

            double bx = bezier(p0x, p1x, p2x, p3x, t);
            double bz = bezier(p0z, p1z, p2z, p3z, t);
            double vx = derivative(p0x, p1x, p2x, p3x, t);
            double vz = derivative(p0z, p1z, p2z, p3z, t);
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
    // Punkt der Mittellinie bei Bogenlänge s: out = { x, y, z, tx, tz }
    public void at(double s, float[] out) {
        int n = count - 1;
        double step = length / n;
        double f = step > 1e-9 ? s / step : 0.0;
        f = Math.max(0.0, Math.min(n, f));
        int i = (int) Math.floor(f);
        if (i >= n) {
            i = n - 1;
        }
        float u = (float) (f - i);
        out[0] = x[i] + (x[i + 1] - x[i]) * u;
        out[1] = y[i] + (y[i + 1] - y[i]) * u;
        out[2] = z[i] + (z[i + 1] - z[i]) * u;
        float ttx = tx[i] + (tx[i + 1] - tx[i]) * u;
        float ttz = tz[i] + (tz[i + 1] - tz[i]) * u;
        float l = (float) Math.hypot(ttx, ttz);
        if (l < 1e-6f) {
            ttx = tx[i];
            ttz = tz[i];
            l = 1.0f;
        }
        out[3] = ttx / l;
        out[4] = ttz / l;
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
