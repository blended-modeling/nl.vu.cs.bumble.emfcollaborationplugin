package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

import java.util.Optional;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emfcloud.modelserver.common.codecs.DefaultJsonCodec;
import org.eclipse.emfcloud.modelserver.common.codecs.EMFJsonConverter;
import org.eclipse.emfcloud.modelserver.common.codecs.EncodingException;

import com.fasterxml.jackson.databind.JsonNode;

public class ConvertHandler {
	public static ConvertHandler getConverter() {
		return new ConvertHandler();
	}
	
	public JsonNode objectToJsonNode(EObject obj) throws EncodingException {
		DefaultJsonCodec converter = new DefaultJsonCodec();
		JsonNode root = null;
		root = converter.basicEncode(obj);
		return root;
	}
	
	public void jsonToEObject(String json) {
		EMFJsonConverter converter = new EMFJsonConverter();
		Optional<EObject> object = converter.fromJson(json);
	}
	
	public Optional<String> toJson(EObject obj) {
		EMFJsonConverter converter = new EMFJsonConverter();
		Optional<String> result = converter.toJson(obj);
		return result;
	}
	
	public Optional<String> toJson(Object obj) {
		EMFJsonConverter converter = new EMFJsonConverter();
		Optional<String> result = converter.toJson(obj);
		return result;
	}

}
