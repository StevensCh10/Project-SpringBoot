package projeto.redes2.project.exceptionhandler;

import lombok.Getter;

@Getter
public enum ProblemType {
	
	//ENTITY_NOT_FOUND_IN_THE_APPEAL("/entity-not-found-in-the-appeal", "Entity not found"),
	RESOURCE_NOT_FOUND("/resource-not-found", "Resource not found"),
	ENTITY_IN_USE("/entity-in-use", "Entity in use"),
	ENTITY_ALREADY_EXISTS("/entity-already-exists", "Entity already exists"),
	PROPERTY_NOT_EXIST("/property-not-exist", "Property not exist"),
	INCOMPREHENSIBLE_MESSAGE("/incomprehensible-message","Incomprehensible message"),
	INVALID_PARAMETER("/invalid-parameter","Invalid Parameter");
	
	private String title;
	private String uri;
	
	private ProblemType(String path, String title) {
		this.uri = "http://localhost:8080" + path;
		this.title = title;
	}
}
