package com.bidv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.springframework.util.ResourceUtils;

public class PGPEncryptDecryptUtil {

	private PGPEncryptDecryptUtil() {
		super();
	}
	/**
	 * 
	 * @param clearData
	 * @param encKey
	 * @param withIntegrityCheck
	 * @param armor
	 * @return
	 * @throws IOException
	 * @throws PGPException
	 * @throws NoSuchProviderException
	 */
	@SuppressWarnings("deprecation")
	public static String encryptByteArray(byte[] clearData, PGPPublicKey encKey, boolean withIntegrityCheck,
			boolean armor) throws IOException, PGPException, NoSuchProviderException {

		ByteArrayOutputStream encOut = new ByteArrayOutputStream();

		OutputStream out = encOut;
		Security.addProvider(new BouncyCastleProvider());
		if (armor) {
			out = new ArmoredOutputStream(out);
		}

		ByteArrayOutputStream bOut = new ByteArrayOutputStream();

		PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
		OutputStream cos = null;
		PGPLiteralDataGenerator lData = null;
		OutputStream pOut = null;
		try {
			cos = comData.open(bOut); // open it with the final

			lData = new PGPLiteralDataGenerator();
			pOut = lData.open(cos, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, clearData.length, // length of clear
																									// data
					new Date() // current time
			);
			pOut.write(clearData);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (pOut != null) {
				pOut.close();
			}
			if (cos != null) {
				cos.close();
			}
			if (lData != null) {
				lData.close();
			}
		}
		comData.close();

		PGPEncryptedDataGenerator cPk = new PGPEncryptedDataGenerator(SymmetricKeyAlgorithmTags.CAST5,
				withIntegrityCheck, new SecureRandom(), "BC");
		cPk.addMethod(encKey);

		byte[] bytes = bOut.toByteArray();
		OutputStream cOut = null;
		try {
			cOut = cPk.open(out, bytes.length);
			cOut.write(bytes); // obtain the actual bytes from the compressed stream
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (cOut != null) {
				cOut.close();
			}
		}

		out.close();

		return encOut.toString();
	}

	public static byte[] decrypt(byte[] encryptedData,PGPPrivateKey sKey) throws IOException, PGPException, NoSuchProviderException {
		Security.addProvider(new BouncyCastleProvider());
       
		InputStream in = new ByteArrayInputStream(encryptedData);
		in = PGPUtil.getDecoderStream(in);
		PGPObjectFactory pgpF = new PGPObjectFactory(in);
		PGPEncryptedDataList enc;
		Object o = pgpF.nextObject();

		if (o instanceof PGPEncryptedDataList) {
			enc = (PGPEncryptedDataList) o;
		} else {
			enc = (PGPEncryptedDataList) pgpF.nextObject();
		}

		Iterator it = enc.getEncryptedDataObjects();
		PGPPublicKeyEncryptedData pbe = null;

		while (it.hasNext()) {
			pbe = (PGPPublicKeyEncryptedData) it.next();
		}

		if (sKey == null) {
			throw new IllegalArgumentException("secret key for message not found.");
		}
		InputStream clear = pbe
				.getDataStream(new JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC").build(sKey));

		PGPObjectFactory plainFact = new PGPObjectFactory(clear);

		PGPCompressedData cData = (PGPCompressedData) plainFact.nextObject();

		PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream());

		PGPLiteralData ld = (PGPLiteralData) pgpFact.nextObject();

		InputStream unc = ld.getInputStream();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int ch;

		while ((ch = unc.read()) >= 0) {
			out.write(ch);
		}

		byte[] returnBytes = out.toByteArray();
		out.close();
		return returnBytes;
	}
	/**
	 * A simple routine that opens a key ring file and loads the first available
	 * key suitable for encryption.
	 * 
	 * @param input
	 * @return
	 * @throws IOException
	 * @throws PGPException
	 */
	@SuppressWarnings("rawtypes")
	public static PGPPublicKey readPublicKey(InputStream input)
			throws IOException, PGPException {
		PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(
				PGPUtil.getDecoderStream(input));

		Iterator keyRingIter = pgpPub.getKeyRings();
		while (keyRingIter.hasNext()) {
			PGPPublicKeyRing keyRing = (PGPPublicKeyRing) keyRingIter.next();

			Iterator keyIter = keyRing.getPublicKeys();
			while (keyIter.hasNext()) {
				PGPPublicKey key = (PGPPublicKey) keyIter.next();

				if (key.isEncryptionKey()) {
					return key;
				}
			}
		}

		throw new IllegalArgumentException(
				"Can't find encryption key in key ring.");
	}

	/**
	 * A simple routine that opens a key ring file and loads the first available
	 * key suitable for signature generation.
	 * 
	 * @param input
	 *            stream to read the secret key ring collection from.
	 * @return a secret key.
	 * @throws IOException
	 *             on a problem with using the input stream.
	 * @throws PGPException
	 *             if there is an issue parsing the input stream.
	 */
	@SuppressWarnings("rawtypes")
	public static PGPSecretKey readSecretKey(InputStream input)
			throws IOException, PGPException {
		PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(
				PGPUtil.getDecoderStream(input));

		//
		// we just loop through the collection till we find a key suitable for
		// encryption, in the real
		// world you would probably want to be a bit smarter about this.
		//

		Iterator keyRingIter = pgpSec.getKeyRings();
		while (keyRingIter.hasNext()) {
			PGPSecretKeyRing keyRing = (PGPSecretKeyRing) keyRingIter.next();

			Iterator keyIter = keyRing.getSecretKeys();
			while (keyIter.hasNext()) {
				PGPSecretKey key = (PGPSecretKey) keyIter.next();

				if (key.isSigningKey()) {
					return key;
				}
			}
		}

		throw new IllegalArgumentException(
				"Can't find signing key in key ring.");
	}

	public static PGPPrivateKey findPrivateKey(PGPSecretKey pgpSecKey,String pass)
			throws PGPException, NoSuchProviderException {
		return pgpSecKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(pass.toCharArray()));
	}
	
	public static String encryptTextMessagePGP(String message, String filePathPublicKey) {
		String encryptData = null;
        File file = null;
		try {
			file = ResourceUtils.getFile("classpath:"+filePathPublicKey);
		} catch (FileNotFoundException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		}
		try (InputStream fileStream = new FileInputStream(file)) {
			PGPPublicKey key = null;
			key = PGPEncryptDecryptUtil.readPublicKey(fileStream);
			encryptData = PGPEncryptDecryptUtil.encryptByteArray(message.getBytes(StandardCharsets.UTF_8), key, true,
					true);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e2) {
			e2.printStackTrace();
		} catch (PGPException e1) {
			e1.printStackTrace();
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
		}
		return encryptData;
	}
	
	public static byte[] decryptTextMessagePGP(byte[] encrypted, String filePathPrivateKey, String passPhrase) {
		Security.addProvider(new BouncyCastleProvider());

		File file = null;
		byte[] bData = null;
		try {
			file = ResourceUtils.getFile("classpath:0x3720DBF7-sec.asc");
		} catch (FileNotFoundException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		}
		try (InputStream fileStream = new FileInputStream(file)) {
			PGPSecretKey key = null;
			key = PGPEncryptDecryptUtil.readSecretKey(fileStream);
			PGPPrivateKey prikey = findPrivateKey(key, passPhrase);
			bData = PGPEncryptDecryptUtil.decrypt(encrypted, prikey);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e2) {
			e2.printStackTrace();
		} catch (PGPException e1) {
			e1.printStackTrace();
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
		}
		return bData;
	}
	
	public static void main(String[] args) {
		Security.addProvider(new BouncyCastleProvider());

		//String canMaHoa = "đây là chuỗi cần mã hóa";
		String canMaHoa ="[{\"transDate\":\"010621\",\"transTime\":\"133059\",\"accountNo\":\"12010001234567\",\"dorc\":\"D\",\"currency\":\"VND\",\"amount\":\"2000000\",\"remark\":\"Thanh toan hoa dơn\"},{\"transDate\":\"010621\",\"transTime\":\"000000\",\"accountNo\":\"12010007654321\",\"dorc\":\"D\",\"currency\":\"VND\",\"amount\": \"3000000\",\"remark\": \"Chuyen tien \"},{\"transDate\": \"010621\",\"accountNo\": \"12010007654321\",\"dorc\": \"C\",\"currency\": \"VND\",\"amount\": \"3000000\",\"remark\": \"abc\"}]";
		//Mã hóa
		String chuoiMaHoa = encryptTextMessagePGP(canMaHoa,"0x3720DBF7-pub.asc");
		System.out.println("Sau chuoiMaHoa="+chuoiMaHoa);
		//Giải mã passPhase = 123456
		byte[] giaiMa = decryptTextMessagePGP(chuoiMaHoa.getBytes(),"0x3720DBF7-sec.asc","123456");		
		System.out.println("Sau chuoiGiaiMa="+ new String(giaiMa, StandardCharsets.UTF_8));	
	}
}
