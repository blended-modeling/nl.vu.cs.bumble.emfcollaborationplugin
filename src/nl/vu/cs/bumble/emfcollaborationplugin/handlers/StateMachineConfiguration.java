package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

import java.util.Collection;

import nl.vu.cs.bumble.statemachine.StatemachinePackage;

import org.eclipse.emfcloud.modelserver.emf.configuration.EPackageConfiguration;
//import com.google.common.collect.Lists;

import com.google.common.collect.Lists;

public class StateMachineConfiguration implements EPackageConfiguration {

	   @Override
	   public String getId() { return StatemachinePackage.eINSTANCE.getNsURI(); }

	   @Override
	   public Collection<String> getFileExtensions() { return Lists.newArrayList("statemachine", "json"); }

	@Override
	public void registerEPackage() {
		StatemachinePackage.eINSTANCE.eClass();
	}

}
