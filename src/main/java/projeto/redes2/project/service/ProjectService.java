package projeto.redes2.project.service;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import projeto.redes2.project.dto.ProjectDTO;
import projeto.redes2.project.exception.EntityAlreadyExists;
import projeto.redes2.project.exception.EntityInUse;
import projeto.redes2.project.exception.EntityNotFound;
import projeto.redes2.project.exception.EntityNotFoundInTheAppeal;
import projeto.redes2.project.model.Project;
import projeto.redes2.project.model.User;
import projeto.redes2.project.repository.ProjectRepository;
import projeto.redes2.project.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class ProjectService {
	
	private final ProjectRepository repository;
	private final UserRepository userRepository;
	private Field field;
	
	public List<ProjectDTO> all(Long userID){
		userRepository.findById(userID).orElseThrow(() -> new EntityNotFoundInTheAppeal(String.format("User with id %d is not registered.", userID)));
		return repository.allProjects(userID).stream().map(ProjectDTO::fromEntity).collect(Collectors.toList());
	}
	
	public ProjectDTO getProject(Long id){
		return ProjectDTO.fromEntity(find(id));
	}

	public Project find(Long id) {
		return repository.findById(id).orElseThrow(() -> new EntityNotFoundInTheAppeal(String.format("Project with id %d is not registered.", id)));
	}
	
	public User findUser(Long id) {
		return userRepository.findById(id).orElseThrow(() -> new EntityNotFound(String.format("User with id %d is not registered.", id)));
	}
	
	@Transactional
	public ProjectDTO add(Project p, User user) {
		Long userID = p.getUser().getId();
		if(repository.findByName(p.getName(), userID) == null) {		
			p.setUser(user);
			return ProjectDTO.fromEntity(repository.saveAndFlush(p));						
		}
		throw new EntityAlreadyExists(String.format("There is already a project called '%s'.", p.getName()));		
	} 
	
	@Transactional
	public ProjectDTO updatePartial(Map<String, Object> fields, Long id, HttpServletRequest request) {
		try {
			Project projectDestiny = find(id);
			
			ObjectMapper objMapper = new ObjectMapper();
			//objMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true); Deixando explicito que quando uma propriedade estiver anotada com @JsonIgnore
																						//  Não é pra aceitar no corpo passado como propriedade.
			objMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true); // Se uma propriedade que não existe for passada no corpo
			Project projectFields = objMapper.convertValue(fields, Project.class);
			
			fields.forEach((propertyName, propertyValue) -> {
				field = ReflectionUtils.findField(Project.class, propertyName);
				field.setAccessible(true);
				
				Object newValue = ReflectionUtils.getField(field, projectFields);	
				ReflectionUtils.setField(field, projectDestiny, newValue);
			});	
			return ProjectDTO.fromEntity(repository.save(projectDestiny));	
		} catch (IllegalArgumentException e) {
			Throwable rootCause = ExceptionUtils.getRootCause(e);
			throw new HttpMessageNotReadableException(e.getMessage(), rootCause, new ServletServerHttpRequest(request));
		}		
	}
	
	@Transactional
	public ProjectDTO update(Project projectAtt, Long id) {
		Project currentProject = find(id);
		Long userID = projectAtt.getUser().getId();
		Project find = repository.findByName(projectAtt.getName(), userID);
		
		if(find != null && find.getId() != id) {
			throw new EntityAlreadyExists(String.format("There is already a project called '%s'.", projectAtt.getName()));	
		}
		
		userRepository.findById(userID).orElseThrow(() -> new EntityNotFound(String.format("User with id %d is not registered.", userID)));
		BeanUtils.copyProperties(projectAtt, currentProject, "id");
		
		return ProjectDTO.fromEntity(repository.saveAndFlush(currentProject));	
	}
	
	@Transactional
	public void delete(Long id) {
		try {
			find(id);
			repository.deleteById(id);		
		}catch(DataIntegrityViolationException e) {
			throw new EntityInUse(String.format("Project with id %d cannot be deleted as it is in use.", id));
		}
	}
}