package io.learnk8s.knote;

import io.minio.MinioClient;
import lombok.*;
import org.apache.commons.io.IOUtils;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class KnoteJavaApplication {

	public static void main(String[] args) {
		SpringApplication.run(KnoteJavaApplication.class, args);
	}

}

@Document(collection = "notes")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
class Note {
	@Id
	private String id;
	private String description;

	@Override
	public String toString() {
		return description;
	}
}

interface NotesRepository extends MongoRepository<Note, String> {

}

//@Configuration
//@EnableConfigurationProperties(KnoteProperties.class)
//class KnoteConfig implements WebMvcConfigurer {
//
//	@Autowired
//	private KnoteProperties properties;
//
//	@Override
//	public void addResourceHandlers(ResourceHandlerRegistry registry) {
//		registry
//				.addResourceHandler("/uploads/**")
//				.addResourceLocations("file:" + properties.getUploadDir())
//				.setCachePeriod(3600)
//				.resourceChain(true)
//				.addResolver(new PathResourceResolver());
//	}
//
//}

//@ConfigurationProperties(prefix = "knote")
//class KnoteProperties {
//	@Value("${uploadDir:/tmp/uploads/}")
//	@Getter
//	private String uploadDir;
//}

@ConfigurationProperties
@ToString
class KnoteProperties {

	@Value("${minio.host}")
	@Getter
	private String minioHost;

	@Value("${minio.bucket:image-storage}")
	@Getter
	private String minioBucket;

	@Value("${minio.access.key}")
	@Getter
	private String minioAccessKey;

	@Value("${minio.secret.key}")
	@Getter
	private String minioSecretKey;

	@Value("${minio.useSSL:false}")
	@Getter
	private boolean minioUseSSL;

	@Value("${minio.reconnect.enabled:true}")
	@Getter
	private boolean minioReconnectEnabled;
}

@Controller
@EnableConfigurationProperties(KnoteProperties.class)
class KNoteController {

	@Autowired
	private NotesRepository notesRepository;
	@Autowired
	private KnoteProperties properties;
	private MinioClient minioClient;
	private Parser parser = Parser.builder().build();
	private HtmlRenderer renderer = HtmlRenderer.builder().build();


	@PostConstruct
	public void init() throws InterruptedException {
		System.out.println(properties);
		initMinio();
	}

	private void initMinio() throws InterruptedException {
		boolean success = false;
		while (!success) {
			try {
				minioClient = new MinioClient("http://" + properties.getMinioHost() + ":9000" ,
						properties.getMinioAccessKey(),
						properties.getMinioSecretKey(),
						false);
				// Check if the bucket already exists.
				boolean isExist = minioClient.bucketExists(properties.getMinioBucket());
				if (isExist) {
					System.out.println("> Bucket already exists.");
				} else {
					minioClient.makeBucket(properties.getMinioBucket());
				}
				success = true;
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("> Minio Reconnect: " + properties.isMinioReconnectEnabled());
				if (properties.isMinioReconnectEnabled()) {
					try {
						Thread.sleep(5000);
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					success = true;
				}
			}
		}
		System.out.println("> Minio initialized!");
	}

	@GetMapping("/")
	public String index(Model model) {
		getAllNotes(model);
		return "index";
	}

	private void getAllNotes(Model model) {
		List<Note> notes = notesRepository.findAll();
		Collections.reverse(notes);
		model.addAttribute("notes", notes);
	}

	private void saveNote(String description, Model model) {
		if (description != null && !description.trim().isEmpty()) {
			Node document = parser.parse(description.trim());
			String html = renderer.render(document);
			notesRepository.save(new Note(null, html));
			//After publish you need to clean up the textarea
			model.addAttribute("description", "");
		}
	}

	@PostMapping("/note")
	public String saveNotes(@RequestParam("image") MultipartFile file,
							@RequestParam String description,
							@RequestParam(required = false) String publish,
							@RequestParam(required = false) String upload,
							Model model) throws Exception {

		if (publish != null && publish.equals("Publish")) {
			saveNote(description, model);
			getAllNotes(model);
			return "redirect:/";
		}
		if (upload != null && upload.equals("Upload")) {
			if (file != null && file.getOriginalFilename() != null &&
					!file.getOriginalFilename().isEmpty()) {
				uploadImage(file, description, model);
			}
			getAllNotes(model);
			return "index";
		}
		return "index";
	}
	private void uploadImage(MultipartFile file, String description, Model model) throws Exception {
		String fileId = UUID.randomUUID().toString() + "." + file.getOriginalFilename().split("\\.")[1];
		//File uploadsDir = new File(properties.getUploadDir());
		//if (!uploadsDir.exists()) {
		//	uploadsDir.mkdir();
		//}
		//file.transferTo(new File(properties.getUploadDir() + fileId));
		//model.addAttribute("description", description + " ![](/uploads/" + fileId + ")");

		minioClient.putObject(
				properties.getMinioBucket(),
				fileId,
				file.getInputStream(),
				file.getSize(),
				null,
				null,
				file.getContentType());

		model.addAttribute(
				"description",
				description + " ![](/img/" + fileId + ")");
	}

	@GetMapping(value = "/img/{name}", produces = MediaType.IMAGE_PNG_VALUE)
	public @ResponseBody byte[] getImageByName(@PathVariable String name) throws Exception {
		InputStream imageStream = minioClient.getObject(properties.getMinioBucket(), name);
		return IOUtils.toByteArray(imageStream);
	}
}