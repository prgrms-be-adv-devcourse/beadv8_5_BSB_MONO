package com.bukang.ai.recordcert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * 기록증 다시 읽기: 원본 사진 + 칸별 상자(꼭짓점 4개) → 칸별 글자. 해석 · 비교 · 판정은 member가 한다
 * 앞처리는 기록증 OCR 명세(specVersion 1)와 기준 구현(onnx_ocr.py의 crop · rec_input · ctc_decode)을 그대로 옮겼다.
 * 브라우저와 같은 조각을 같은 글자로 읽어야 "다름"이 조작 신호가 되므로, 값을 바꾸면 SPEC_VERSION을 올리고 골든 세트를 다시 만든다
 */
@Component
public class CertReader implements AutoCloseable {
	public static final String SPEC_VERSION = "1";
	public static final String REC_MODEL_SHA256 = "cd6e2ea50f6943ca7271eb8c56a877a5a90720b7047fe9c41a2e541a25773c9b";
	static final String DICT_SHA256 = "a88071c68c01707489baa79ebe0405b7beb5cca229f4fc94cc3ef992328802d7";
	private static final String MODEL = "/recordcert/korean_PP-OCRv5_rec_mobile.onnx";
	private static final String DICT = "/recordcert/ppocrv5_korean_dict.txt";
	private static final int REC_H = 48;
	private static final int MIN_W = 320;
	private static final int MAX_W = 3200;
	// 사진 픽셀 상한 2500만(휴대폰 기본 12MP · 24MP는 통과). 풀면 BGR 3바이트라 약 75MB로 512Mi 안에 든다.
	// 48MP 원본 모드 사진은 약 144MB라 읽지 않고 실패(→ 심사 필요)로 넘긴다
	static final long MAX_PIXELS = 25_000_000L;
	// 브라우저 좌표 변환의 부동소수 오차를 받아 주는 폭(픽셀). 이만큼 사진 밖이어도 읽는다
	private static final float EDGE_TOLERANCE = 0.5f;

	private final OrtEnvironment env = OrtEnvironment.getEnvironment();
	private final OrtSession session;
	private final String inputName;
	private final String[] chars;

	public CertReader() {
		this(resource(MODEL), resource(DICT));
	}

	// 서버가 뜰 때 모델 · 사전이 명세와 같은지 확인한다. 다르면 브라우저와 다른 글자를 낼 수 있어 뜨지 않는다
	CertReader(byte[] model, byte[] dict) {
		checkSha256("인식 모델", model, REC_MODEL_SHA256);
		checkSha256("인식 사전", dict, DICT_SHA256);
		nu.pattern.OpenCV.loadLocally();
		List<String> lines = new String(dict, StandardCharsets.UTF_8).lines().toList();
		chars = new String[lines.size() + 2]; // blank(0) + 사전 줄 + 공백
		chars[0] = "";
		for (int i = 0; i < lines.size(); i++) {
			chars[i + 1] = lines.get(i);
		}
		chars[chars.length - 1] = " ";
		try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
			// 한 번에 한 장만 읽는다(512Mi). 스레드 1개로 잰 메모리 · 결과가 기준이다
			opts.setIntraOpNumThreads(1);
			opts.setInterOpNumThreads(1);
			session = env.createSession(model, opts);
			inputName = session.getInputNames().iterator().next();
		} catch (OrtException e) {
			throw new IllegalStateException("인식 모델을 올리지 못함", e);
		}
	}

	/**
	 * 칸별로 상자 자리를 잘라 읽는다. 돌려준 Map에 없는 칸은 읽지 못함(상자 없음 · 상자가 사진 밖 · 글자 없음)
	 * 브라우저의 명세 버전 · 인식 모델이 서버와 다르면 읽지 않고 모든 칸을 읽지 못함으로 둔다
	 *
	 * @throws IllegalArgumentException 사진을 열 수 없을 때 (깨진 파일 → 심사 필요로 넘길 실패)
	 */
	public Map<CertField, String> read(byte[] image, Map<CertField, float[][]> boxes, String specVersion,
		String recModelSha256) {
		Map<CertField, String> texts = new EnumMap<>(CertField.class);
		if (!SPEC_VERSION.equals(specVersion) || !REC_MODEL_SHA256.equals(recModelSha256)) {
			return texts;
		}
		Mat img = decode(image);
		try {
			boxes.forEach((field, box) -> {
				if (!inside(box, img.cols(), img.rows())) {
					return;
				}
				Mat piece = crop(img, box);
				try {
					String text = piece.empty() ? "" : recognize(piece);
					if (!text.isBlank()) {
						texts.put(field, text);
					}
				} finally {
					piece.release();
				}
			});
		} finally {
			img.release();
		}
		return texts;
	}

	// 이미 자른 조각 하나를 읽는다. 골든 세트(기준 구현이 자른 조각 → 글자)를 확인하는 자리
	String readPiece(byte[] piece) {
		Mat img = decode(piece);
		try {
			return recognize(img);
		} finally {
			img.release();
		}
	}

	// cv2.imdecode(IMREAD_COLOR)와 같다: BGR, EXIF 회전 적용 (브라우저 좌표도 EXIF 적용 방향 기준)
	private static Mat decode(byte[] bytes) {
		long pixels = ImageHeader.pixels(bytes);
		if (pixels <= 0 || pixels > MAX_PIXELS) {
			throw new IllegalArgumentException("기록증 사진 크기를 알 수 없거나 너무 큼: " + pixels + "픽셀");
		}
		MatOfByte buf = new MatOfByte(bytes);
		Mat img = Imgcodecs.imdecode(buf, Imgcodecs.IMREAD_COLOR);
		buf.release();
		if (img.empty()) {
			throw new IllegalArgumentException("기록증 사진을 열 수 없음");
		}
		return img;
	}

	// 꼭짓점 4개가 모두 사진 안(경계 0.5px 허용)에 있어야 읽는다. 밖을 가리키는 큰 상자로 메모리를 쓰게 하지 않으려고
	private static boolean inside(float[][] box, int width, int height) {
		if (box == null || box.length != 4) {
			return false;
		}
		for (float[] p : box) {
			if (p == null || p.length != 2 || !(within(p[0], width) && within(p[1], height))) {
				return false;
			}
		}
		return true;
	}

	private static boolean within(float value, int size) {
		return value >= -EDGE_TOLERANCE && value <= size + EDGE_TOLERANCE;
	}

	// onnx_ocr.crop: minAreaRect 꼭짓점 → 원근 보정(cubic, replicate) → 높이/너비 >= 1.5면 반시계 90도
	static Mat crop(Mat img, float[][] box) {
		Point[] in = new Point[4];
		for (int i = 0; i < 4; i++) {
			in[i] = new Point((int)box[i][0], (int)box[i][1]); // np.astype(np.int32)와 같은 버림
		}
		MatOfPoint2f inMat = new MatOfPoint2f(in);
		RotatedRect rect = Imgproc.minAreaRect(inMat);
		Mat bp = new Mat();
		Imgproc.boxPoints(rect, bp);
		float[] raw = new float[8];
		bp.get(0, 0, raw);
		inMat.release();
		bp.release();
		float[][] corners = new float[4][];
		for (int i = 0; i < 4; i++) {
			corners[i] = new float[] {raw[i * 2], raw[i * 2 + 1]};
		}
		Arrays.sort(corners, (a, b) -> Float.compare(a[0], b[0])); // sorted(key=x)와 같은 안정 정렬
		float[] topLeft = corners[1][1] > corners[0][1] ? corners[0] : corners[1];
		float[] bottomLeft = corners[1][1] > corners[0][1] ? corners[1] : corners[0];
		float[] topRight = corners[3][1] > corners[2][1] ? corners[2] : corners[3];
		float[] bottomRight = corners[3][1] > corners[2][1] ? corners[3] : corners[2];
		int cw = (int)Math.max(norm(topLeft, topRight), norm(bottomRight, bottomLeft));
		int ch = (int)Math.max(norm(topLeft, bottomLeft), norm(topRight, bottomRight));
		if (cw < 1 || ch < 1) {
			return new Mat(); // 넓이 없는 상자
		}
		MatOfPoint2f src = new MatOfPoint2f(pt(topLeft), pt(topRight), pt(bottomRight), pt(bottomLeft));
		MatOfPoint2f dst = new MatOfPoint2f(new Point(0, 0), new Point(cw, 0), new Point(cw, ch), new Point(0, ch));
		Mat transform = Imgproc.getPerspectiveTransform(src, dst);
		Mat out = new Mat();
		Imgproc.warpPerspective(img, out, transform, new Size(cw, ch), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);
		src.release();
		dst.release();
		transform.release();
		if ((double)out.rows() / out.cols() >= 1.5) {
			Mat rot = new Mat();
			Core.rotate(out, rot, Core.ROTATE_90_COUNTERCLOCKWISE); // np.rot90
			out.release();
			return rot;
		}
		return out;
	}

	// np.linalg.norm(float32): float32로 제곱합 · 제곱근
	private static float norm(float[] from, float[] to) {
		float dx = from[0] - to[0];
		float dy = from[1] - to[1];
		return (float)Math.sqrt(dx * dx + dy * dy);
	}

	private static Point pt(float[] xy) {
		return new Point(xy[0], xy[1]);
	}

	// onnx_ocr.rec_input(조각 하나씩, batch 1) + 인식 + ctc_decode
	private String recognize(Mat piece) {
		int height = piece.rows();
		int width = piece.cols();
		int padW = Math.min(MAX_W, (int)(REC_H * Math.max((double)MIN_W / REC_H, (double)width / height)));
		int rw = Math.min((int)Math.ceil((double)REC_H * width / height), padW);
		Mat resized = new Mat();
		Imgproc.resize(piece, resized, new Size(rw, REC_H), 0, 0, Imgproc.INTER_LINEAR);
		byte[] px = new byte[REC_H * rw * 3];
		resized.get(0, 0, px);
		resized.release();
		float[] input = new float[3 * REC_H * padW]; // 오른쪽은 0으로 채움
		for (int y = 0; y < REC_H; y++) {
			for (int col = 0; col < rw; col++) {
				for (int c = 0; c < 3; c++) {
					float value = (px[(y * rw + col) * 3 + c] & 0xff) / 255f;
					input[c * REC_H * padW + y * padW + col] = (value - 0.5f) / 0.5f;
				}
			}
		}
		try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), new long[] {1, 3, REC_H, padW});
			OrtSession.Result r = session.run(Collections.singletonMap(inputName, t))) {
			OnnxTensor out = (OnnxTensor)r.get(0);
			long[] shape = out.getInfo().getShape();
			if (shape[2] != chars.length) {
				throw new IllegalStateException("사전 크기 " + chars.length + "와 모델 출력 " + shape[2] + "이 다름");
			}
			return ctcDecode(out.getFloatBuffer(), (int)shape[1], (int)shape[2]);
		} catch (OrtException e) {
			throw new IllegalStateException("인식 모델 실행 실패", e);
		}
	}

	// CTC greedy: 시점마다 가장 높은 글자, 같은 글자 연속 제거, blank(0) 제거
	private String ctcDecode(FloatBuffer prob, int steps, int classes) {
		StringBuilder text = new StringBuilder();
		int prev = -1;
		for (int t = 0; t < steps; t++) {
			int best = 0;
			float bestP = prob.get(t * classes);
			for (int k = 1; k < classes; k++) {
				float score = prob.get(t * classes + k);
				if (score > bestP) { // np.argmax: 같으면 앞 번호
					bestP = score;
					best = k;
				}
			}
			if (best != 0 && best != prev) {
				text.append(chars[best]);
			}
			prev = best;
		}
		return text.toString();
	}

	private static byte[] resource(String path) {
		try (InputStream in = CertReader.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException(path + " 리소스가 없음");
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new IllegalStateException(path + " 리소스를 읽지 못함", e);
		}
	}

	private static void checkSha256(String name, byte[] bytes, String expected) {
		try {
			String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			if (!actual.equals(expected)) {
				throw new IllegalStateException(name + " sha256이 명세와 다름: " + actual);
			}
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void close() {
		try {
			session.close();
		} catch (OrtException e) {
			throw new IllegalStateException(e);
		}
	}
}
