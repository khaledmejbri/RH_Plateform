package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PageReferentielResponse<T> {

	@JsonProperty("contenu")
	private List<T> contenu;

	@JsonProperty("total_elements")
	private long totalElements;

	@JsonProperty("total_pages")
	private int totalPages;

	@JsonProperty("page")
	private int page;

	@JsonProperty("taille")
	private int taille;

	public PageReferentielResponse() {
	}

	public PageReferentielResponse(List<T> contenu, long totalElements, int totalPages, int page, int taille) {
		this.contenu = contenu;
		this.totalElements = totalElements;
		this.totalPages = totalPages;
		this.page = page;
		this.taille = taille;
	}

	public List<T> getContenu() {
		return contenu;
	}

	public void setContenu(List<T> contenu) {
		this.contenu = contenu;
	}

	public long getTotalElements() {
		return totalElements;
	}

	public void setTotalElements(long totalElements) {
		this.totalElements = totalElements;
	}

	public int getTotalPages() {
		return totalPages;
	}

	public void setTotalPages(int totalPages) {
		this.totalPages = totalPages;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getTaille() {
		return taille;
	}

	public void setTaille(int taille) {
		this.taille = taille;
	}
}
