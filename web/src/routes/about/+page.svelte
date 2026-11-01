<script lang="ts">
	import tmdbLogo from '#lib/assets/tmdb-logo.svg';
</script>

<svelte:head>
	<title>About · Movie Catalog</title>
</svelte:head>

<h1>About</h1>
<p>Movie Catalog keeps a list of movies, the people who made them, and their artwork.</p>

<section class="panel credits" aria-labelledby="credits-heading">
	<h2 id="credits-heading">Credits</h2>
	<a href="https://www.themoviedb.org/" rel="noopener noreferrer" target="_blank">
		<img src={tmdbLogo} alt="The Movie Database (TMDB)" width="139" height="18" />
	</a>
	<p>This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
	<p class="muted">
		Posters found with “Find poster on TMDB”, and the demo posters when the seed is given a TMDB
		token, come from The Movie Database (TMDB).
	</p>
</section>

<style>
	.credits {
		display: grid;
		gap: 0.75rem;
		justify-items: start;
		max-width: 640px;
	}

	h2 {
		margin: 0;
		font-size: 1.1rem;
	}

	p {
		margin: 0;
	}

	img {
		display: block;
	}
</style>
