package com.codesmell.app.controller;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.codesmell.app.dao.CommitAnalysisDao;
import com.codesmell.app.dao.CommitDao;
import com.codesmell.app.dao.ProjectDao;
import com.codesmell.app.dao.UserDao;
import com.codesmell.app.model.CommitAnalysis;
import com.codesmell.app.model.Project;
import com.codesmell.app.model.User;
import com.codesmell.app.sonar.SonarAnalysis;

@Controller
class ProjectController {

	@Autowired
	private ProjectDao projectDao;
	@Autowired
	private CommitAnalysisDao commitAnalysisDao;
	@Autowired
	private CommitDao commitDao;
	@Autowired
	private UserDao userDao;

	@PostMapping("/createNewProject")
	public String createNewProject(Model model, @ModelAttribute Project project, HttpServletRequest req,
			HttpServletResponse resp) {
		String emailSt = (String) req.getSession().getAttribute("email");
		model.addAttribute("email", emailSt);
		project.setEmail(emailSt);
		if (projectDao.findByurl(project.getUrl()).length == 0)
			if (projectDao.findByprojectName(project.getProjectName()).length == 0) {
				projectDao.save(project);
				writeConfigFile(project);
			}
		model.addAttribute("projects", getProjects(emailSt));
		return "landingPage";
	}
	
	@PostMapping("/runAnalysis")
	public String newProject(Model model, @ModelAttribute Project projectToSend,HttpServletRequest req, HttpServletResponse resp) {
		String projectName = projectToSend.getProjectName();	
		Project p = projectDao.findByprojectName(projectName)[0];
		CommitAnalysis ca = new CommitAnalysis();
		ca.setIdProject(p.getProjectName());
		ca.setConfigurationFile(projectName+".properties");
		commitAnalysisDao.insert(ca);
		SonarAnalysis so = new SonarAnalysis(commitAnalysisDao,commitDao);	
		so.setAnalysis(ca);
		so.setProject(p);
		so.start();
		configureModelLandingPage(model, (String) req.getSession().getAttribute("email"));
		return "landingPage";
	}
	
	private void writeConfigFile(Project project) {
		try {
			File file = new File((project.getProjectName() + ".properties"));
			PrintWriter writer = new PrintWriter(file);
			writer.println("# Required metadata");
			writer.println("sonar.projectKey=" + project.getIdSonarKey());
			writer.println("sonar.projectName=" + project.getProjectName());
			writer.println("sonar.projectVersion=" + project.getSonarVersion());
			writer.println("sonar.host.url=http://sonar.inf.unibz.it/");
			writer.println("# Comma-separated paths to directories with sources (required)");
			writer.println("sonar.sources=.");
			writer.println("# Language");
			writer.println("sonar.language=java");
			writer.println("# Encoding of the source files");
			writer.println("sonar.sourceEncoding=UTF-8");
			writer.println("gitRepo=" + project.getUrl());
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	private List<Project> getProjects(String email) {
		List<Project> projects = projectDao.findByemail(email);
		for (Project p : projects) {
			String url = p.getUrl();
			FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
			if (p.getAnalysedCommits() == 0
					|| (((new Date().getTime() - p.getLastRequest().getTime()) / 1000 / 3600) > 6)) {
				int count = getCommitsCount(url);
				p.setTotalCommits(count);
				p.setLastRequest(new Date());
			}
			p.setAnalysedCommits(commitDao.findByprojectName(p.getProjectName()).size());
			projectDao.save(p);
		}
		return projects;
	}
	

	private int getCommitsCount(String url) {
		int count = 0;
		File d = new File("directory");
		Git git;
		try {
			git = Git.cloneRepository().setURI(url).setDirectory(d).call();
			Iterable<RevCommit> commits = git.log().call();
			for (RevCommit commit : commits)
				count++;
			FileUtils.deleteDirectory(d);
		} catch (InvalidRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return count;
	}

	private void configureModelLandingPage(Model model, String email) {
		model.addAttribute("projects", getProjects(email));
		model.addAttribute("email", email);
		model.addAttribute("projectToSend", new Project());
	}
}
