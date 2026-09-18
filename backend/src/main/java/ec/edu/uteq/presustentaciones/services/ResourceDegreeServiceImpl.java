package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.SaveResourceRequest;
import ec.edu.uteq.presustentaciones.dto.ResourceDegreeDTO;
import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.ResourceDegree;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.ResourceDegreeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceDegreeServiceImpl implements ResourceDegreeService {

    private final ResourceDegreeRepository resourceRepository;
    private final ProgramRepository programRepository;

    /**
     * Resources visibles para una program (los generales + los de esa program).
     *
     * @param programId id de la program a filtrar, o {@code null} para list todos
     * @return los resources de titulación visibles
     */
    @Override
    @Transactional(readOnly = true)
    public List<ResourceDegreeDTO> list(Integer programId) {
        List<ResourceDegree> resources = programId == null
                ? resourceRepository.listAll()
                : resourceRepository.listVisiblesForProgram(programId);
        return resources.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /**
     * @param request datos del resource a create
     * @return el resource creado
     * @throws IllegalArgumentException si la program indicada no existe
     */
    @Override
    @Transactional
    public ResourceDegreeDTO create(SaveResourceRequest request) {
        ResourceDegree resource = ResourceDegree.builder()
                .titulo(request.getTitulo().trim())
                .categoria(request.getCategoria().trim())
                .urlFile(request.getUrlFile().trim())
                .program(resolveProgram(request.getProgramId()))
                .build();
        return mapToDTO(resourceRepository.save(resource));
    }

    /**
     * @param id      id del resource a update
     * @param request datos nuevos del resource
     * @return el resource actualizado
     * @throws IllegalArgumentException si el resource o la program indicada no existen
     */
    @Override
    @Transactional
    public ResourceDegreeDTO update(Integer id, SaveResourceRequest request) {
        ResourceDegree resource = resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recurso no encontrado"));
        resource.setTitulo(request.getTitulo().trim());
        resource.setCategoria(request.getCategoria().trim());
        resource.setUrlFile(request.getUrlFile().trim());
        resource.setProgram(resolveProgram(request.getProgramId()));
        return mapToDTO(resourceRepository.save(resource));
    }

    /**
     * @param id id del resource a delete
     * @throws IllegalArgumentException si el resource no existe
     */
    @Override
    @Transactional
    public void delete(Integer id) {
        if (!resourceRepository.existsById(id)) {
            throw new IllegalArgumentException("Recurso no encontrado");
        }
        resourceRepository.deleteById(id);
    }

    private Program resolveProgram(Integer programId) {
        if (programId == null) {
            return null;
        }
        return programRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Carrera no encontrada"));
    }

    private ResourceDegreeDTO mapToDTO(ResourceDegree r) {
        return ResourceDegreeDTO.builder()
                .id(r.getId())
                .titulo(r.getTitulo())
                .categoria(r.getCategoria())
                .urlFile(r.getUrlFile())
                .programId(r.getProgram() != null ? r.getProgram().getId() : null)
                .programNombre(r.getProgram() != null ? r.getProgram().getNombre() : null)
                .build();
    }
}
