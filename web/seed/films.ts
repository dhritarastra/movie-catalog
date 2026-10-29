/**
 * The demo catalogue: real films with facts only (titles, US release dates, runtimes, credits)
 * and synopses and bios written for this project. Release dates are the first US release,
 * limited or wide. Credits are the main cast and the directors and writers, plus a few producers.
 * Credits name people by their `name` here; the seed looks up the ID it created for each.
 * The movie list shows movies in the order written here, 12 to a page.
 */

export interface SeedPerson {
	name: string;
	bio?: string;
	birthDate?: string;
}

export type CreatorRole = 'DIRECTOR' | 'WRITER' | 'PRODUCER';

export interface SeedMovie {
	title: string;
	synopsis: string;
	releaseDate: string;
	runtimeMinutes: number;
	genres: string[];
	/** False leaves the movie without artwork, so the placeholder can be seen too. */
	poster: boolean;
	/** In billing order. */
	cast: { name: string; character: string }[];
	creators: { name: string; role: CreatorRole }[];
}

export const people: SeedPerson[] = [
	{ name: 'Denis Villeneuve', bio: 'Canadian director.', birthDate: '1967-10-03' },
	{ name: 'Eric Heisserer', bio: 'American screenwriter.' },
	{ name: 'Amy Adams', bio: 'American actor.', birthDate: '1974-08-20' },
	{ name: 'Jeremy Renner', bio: 'American actor.', birthDate: '1971-01-07' },
	{ name: 'Forest Whitaker', bio: 'American actor and director.', birthDate: '1961-07-15' },
	{ name: 'Hampton Fancher', bio: 'American screenwriter and actor.' },
	{ name: 'Michael Green', bio: 'American screenwriter.' },
	{ name: 'Ryan Gosling', bio: 'Canadian actor.', birthDate: '1980-11-12' },
	{ name: 'Harrison Ford', bio: 'American actor.', birthDate: '1942-07-13' },
	{ name: 'Ana de Armas', bio: 'Cuban and Spanish actor.', birthDate: '1988-04-30' },
	{ name: 'Jon Spaihts', bio: 'American screenwriter.' },
	{ name: 'Eric Roth', bio: 'American screenwriter.' },
	{ name: 'Timothée Chalamet', bio: 'American and French actor.', birthDate: '1995-12-27' },
	{ name: 'Rebecca Ferguson', bio: 'Swedish actor.', birthDate: '1983-10-19' },
	{ name: 'Oscar Isaac', bio: 'Guatemalan-born American actor.', birthDate: '1979-03-09' },
	{ name: 'Christopher Nolan', bio: 'British and American director.', birthDate: '1970-07-30' },
	{ name: 'Emma Thomas', bio: 'British film producer.' },
	{ name: 'Jonathan Nolan', bio: 'British and American screenwriter.' },
	{ name: 'Leonardo DiCaprio', bio: 'American actor.', birthDate: '1974-11-11' },
	{ name: 'Joseph Gordon-Levitt', bio: 'American actor.', birthDate: '1981-02-17' },
	{ name: 'Elliot Page', bio: 'Canadian actor.', birthDate: '1987-02-21' },
	{ name: 'Matthew McConaughey', bio: 'American actor.', birthDate: '1969-11-04' },
	{ name: 'Anne Hathaway', bio: 'American actor.', birthDate: '1982-11-12' },
	{ name: 'Jessica Chastain', bio: 'American actor.', birthDate: '1977-03-24' },
	{ name: 'Cillian Murphy', bio: 'Irish actor.', birthDate: '1976-05-25' },
	{ name: 'Emily Blunt', bio: 'British actor.', birthDate: '1983-02-23' },
	{ name: 'Robert Downey Jr.', bio: 'American actor.', birthDate: '1965-04-04' },
	{ name: 'Matt Damon', bio: 'American actor.', birthDate: '1970-10-08' },
	{ name: 'Bong Joon Ho', bio: 'South Korean director.', birthDate: '1969-09-14' },
	{ name: 'Han Jin-won', bio: 'South Korean screenwriter.' },
	{ name: 'Song Kang-ho', bio: 'South Korean actor.', birthDate: '1967-01-17' },
	{ name: 'Choi Woo-shik', bio: 'South Korean actor.' },
	{ name: 'Park So-dam', bio: 'South Korean actor.' },
	{ name: 'Hayao Miyazaki', bio: 'Japanese animator and director.', birthDate: '1941-01-05' },
	{ name: 'Toshio Suzuki', bio: 'Japanese film producer.' },
	{ name: 'Rumi Hiiragi', bio: 'Japanese actor.' },
	{ name: 'Miyu Irino', bio: 'Japanese actor.' },
	{ name: 'Jean-Pierre Jeunet', bio: 'French director.' },
	{ name: 'Guillaume Laurant', bio: 'French screenwriter.' },
	{ name: 'Audrey Tautou', bio: 'French actor.', birthDate: '1976-08-09' },
	{ name: 'Mathieu Kassovitz', bio: 'French actor and director.' },
	{ name: 'George Miller', bio: 'Australian director.', birthDate: '1945-03-03' },
	{ name: 'Brendan McCarthy', bio: 'British comics artist and screenwriter.' },
	{ name: 'Nico Lathouris', bio: 'Australian screenwriter and actor.' },
	{ name: 'Tom Hardy', bio: 'British actor.', birthDate: '1977-09-15' },
	{ name: 'Charlize Theron', bio: 'South African and American actor.', birthDate: '1975-08-07' },
	{ name: 'Nicholas Hoult', bio: 'British actor.', birthDate: '1989-12-07' },
	{ name: 'Wes Anderson', bio: 'American director.', birthDate: '1969-05-01' },
	{ name: 'Hugo Guinness', bio: 'British illustrator and writer.' },
	{ name: 'Ralph Fiennes', bio: 'British actor.', birthDate: '1962-12-22' },
	{ name: 'Tony Revolori', bio: 'American actor.' },
	{ name: 'Saoirse Ronan', bio: 'Irish and American actor.', birthDate: '1994-04-12' },
	{ name: 'Daniel Kwan', bio: 'American director and writer.' },
	{ name: 'Daniel Scheinert', bio: 'American director and writer.' },
	{ name: 'Michelle Yeoh', bio: 'Malaysian actor.', birthDate: '1962-08-06' },
	{ name: 'Ke Huy Quan', bio: 'American actor.', birthDate: '1971-08-20' },
	{ name: 'Jamie Lee Curtis', bio: 'American actor.', birthDate: '1958-11-22' },
	{ name: 'Lana Wachowski', bio: 'American director and writer.' },
	{ name: 'Lilly Wachowski', bio: 'American director and writer.' },
	{ name: 'Keanu Reeves', bio: 'Canadian actor.', birthDate: '1964-09-02' },
	{ name: 'Laurence Fishburne', bio: 'American actor.', birthDate: '1961-07-30' },
	{ name: 'Carrie-Anne Moss', bio: 'Canadian actor.', birthDate: '1967-08-21' }
];

export const movies: SeedMovie[] = [
	{
		title: 'Arrival',
		synopsis:
			'A linguist is asked to talk with visitors whose ships have settled over the Earth, and finds that their language changes how she remembers.',
		releaseDate: '2016-11-11',
		runtimeMinutes: 116,
		genres: ['Science Fiction', 'Drama'],
		poster: true,
		cast: [
			{ name: 'Amy Adams', character: 'Louise Banks' },
			{ name: 'Jeremy Renner', character: 'Ian Donnelly' },
			{ name: 'Forest Whitaker', character: 'Colonel Weber' }
		],
		creators: [
			{ name: 'Denis Villeneuve', role: 'DIRECTOR' },
			{ name: 'Eric Heisserer', role: 'WRITER' }
		]
	},
	{
		title: 'Blade Runner 2049',
		synopsis:
			'Thirty years on, a replicant hunter in Los Angeles turns up a secret that leads him to a man who has been missing for decades.',
		releaseDate: '2017-10-06',
		runtimeMinutes: 164,
		genres: ['Science Fiction', 'Thriller'],
		poster: true,
		cast: [
			{ name: 'Ryan Gosling', character: 'K' },
			{ name: 'Harrison Ford', character: 'Rick Deckard' },
			{ name: 'Ana de Armas', character: 'Joi' }
		],
		creators: [
			{ name: 'Denis Villeneuve', role: 'DIRECTOR' },
			{ name: 'Hampton Fancher', role: 'WRITER' },
			{ name: 'Michael Green', role: 'WRITER' }
		]
	},
	{
		title: 'Dune',
		synopsis:
			'The heir of a noble house goes with his family to rule a desert planet, the only source of the most valuable substance in the universe.',
		releaseDate: '2021-10-22',
		runtimeMinutes: 155,
		genres: ['Science Fiction', 'Adventure'],
		poster: true,
		cast: [
			{ name: 'Timothée Chalamet', character: 'Paul Atreides' },
			{ name: 'Rebecca Ferguson', character: 'Lady Jessica' },
			{ name: 'Oscar Isaac', character: 'Duke Leto Atreides' }
		],
		creators: [
			{ name: 'Denis Villeneuve', role: 'DIRECTOR' },
			{ name: 'Jon Spaihts', role: 'WRITER' },
			{ name: 'Denis Villeneuve', role: 'WRITER' },
			{ name: 'Eric Roth', role: 'WRITER' }
		]
	},
	{
		title: 'Inception',
		synopsis:
			'A thief who steals secrets from dreams is offered one last job: to plant an idea instead.',
		releaseDate: '2010-07-16',
		runtimeMinutes: 148,
		genres: ['Science Fiction', 'Action'],
		poster: true,
		cast: [
			{ name: 'Leonardo DiCaprio', character: 'Cobb' },
			{ name: 'Joseph Gordon-Levitt', character: 'Arthur' },
			{ name: 'Elliot Page', character: 'Ariadne' }
		],
		creators: [
			{ name: 'Christopher Nolan', role: 'DIRECTOR' },
			{ name: 'Christopher Nolan', role: 'WRITER' },
			{ name: 'Emma Thomas', role: 'PRODUCER' }
		]
	},
	{
		title: 'Interstellar',
		synopsis:
			'With crops failing across the Earth, a former pilot leaves his children to cross a wormhole in search of a new home.',
		releaseDate: '2014-11-05',
		runtimeMinutes: 169,
		genres: ['Science Fiction', 'Drama'],
		poster: true,
		cast: [
			{ name: 'Matthew McConaughey', character: 'Cooper' },
			{ name: 'Anne Hathaway', character: 'Brand' },
			{ name: 'Jessica Chastain', character: 'Murph' }
		],
		creators: [
			{ name: 'Christopher Nolan', role: 'DIRECTOR' },
			{ name: 'Jonathan Nolan', role: 'WRITER' },
			{ name: 'Christopher Nolan', role: 'WRITER' },
			{ name: 'Emma Thomas', role: 'PRODUCER' }
		]
	},
	{
		title: 'Oppenheimer',
		synopsis:
			'The physicist who led the building of the first atomic bomb, from the desert test site to the hearing that took away his clearance.',
		releaseDate: '2023-07-21',
		runtimeMinutes: 180,
		genres: ['Drama', 'History'],
		poster: true,
		cast: [
			{ name: 'Cillian Murphy', character: 'J. Robert Oppenheimer' },
			{ name: 'Emily Blunt', character: 'Kitty Oppenheimer' },
			{ name: 'Matt Damon', character: 'Leslie Groves' },
			{ name: 'Robert Downey Jr.', character: 'Lewis Strauss' }
		],
		creators: [
			{ name: 'Christopher Nolan', role: 'DIRECTOR' },
			{ name: 'Christopher Nolan', role: 'WRITER' },
			{ name: 'Emma Thomas', role: 'PRODUCER' }
		]
	},
	{
		title: 'Parasite',
		synopsis:
			'A poor family talks its way, one by one, into jobs with a rich one, until the house turns out to hold a secret of its own.',
		releaseDate: '2019-10-11',
		runtimeMinutes: 132,
		genres: ['Thriller', 'Drama'],
		poster: true,
		cast: [
			{ name: 'Song Kang-ho', character: 'Kim Ki-taek' },
			{ name: 'Choi Woo-shik', character: 'Kim Ki-woo' },
			{ name: 'Park So-dam', character: 'Kim Ki-jung' }
		],
		creators: [
			{ name: 'Bong Joon Ho', role: 'DIRECTOR' },
			{ name: 'Bong Joon Ho', role: 'WRITER' },
			{ name: 'Han Jin-won', role: 'WRITER' }
		]
	},
	{
		title: 'Spirited Away',
		synopsis:
			'A girl moving house wanders into a world of spirits and must work in a bathhouse to free her parents.',
		releaseDate: '2002-09-20',
		runtimeMinutes: 125,
		genres: ['Animation', 'Fantasy'],
		poster: true,
		cast: [
			{ name: 'Rumi Hiiragi', character: 'Chihiro' },
			{ name: 'Miyu Irino', character: 'Haku' }
		],
		creators: [
			{ name: 'Hayao Miyazaki', role: 'DIRECTOR' },
			{ name: 'Hayao Miyazaki', role: 'WRITER' },
			{ name: 'Toshio Suzuki', role: 'PRODUCER' }
		]
	},
	{
		title: 'Amélie',
		synopsis:
			'A shy waitress in Montmartre quietly arranges small changes in the lives of the people around her, and has trouble doing the same for herself.',
		releaseDate: '2001-11-02',
		runtimeMinutes: 122,
		genres: ['Comedy', 'Romance'],
		poster: true,
		cast: [
			{ name: 'Audrey Tautou', character: 'Amélie Poulain' },
			{ name: 'Mathieu Kassovitz', character: 'Nino Quincampoix' }
		],
		creators: [
			{ name: 'Jean-Pierre Jeunet', role: 'DIRECTOR' },
			{ name: 'Guillaume Laurant', role: 'WRITER' },
			{ name: 'Jean-Pierre Jeunet', role: 'WRITER' }
		]
	},
	{
		title: 'Mad Max: Fury Road',
		synopsis:
			'In a desert wasteland, a drifter and a rebel commander flee across the sand with the wives of a warlord, and his army in pursuit.',
		releaseDate: '2015-05-15',
		runtimeMinutes: 120,
		genres: ['Action', 'Adventure'],
		poster: true,
		cast: [
			{ name: 'Tom Hardy', character: 'Max Rockatansky' },
			{ name: 'Charlize Theron', character: 'Imperator Furiosa' },
			{ name: 'Nicholas Hoult', character: 'Nux' }
		],
		creators: [
			{ name: 'George Miller', role: 'DIRECTOR' },
			{ name: 'George Miller', role: 'WRITER' },
			{ name: 'Brendan McCarthy', role: 'WRITER' },
			{ name: 'Nico Lathouris', role: 'WRITER' }
		]
	},
	{
		title: 'The Grand Budapest Hotel',
		synopsis:
			'The concierge of a famous mountain hotel and his young lobby boy are caught up in the theft of a priceless painting.',
		releaseDate: '2014-03-07',
		runtimeMinutes: 99,
		genres: ['Comedy'],
		poster: true,
		cast: [
			{ name: 'Ralph Fiennes', character: 'M. Gustave' },
			{ name: 'Tony Revolori', character: 'Zero Moustafa' },
			{ name: 'Saoirse Ronan', character: 'Agatha' }
		],
		creators: [
			{ name: 'Wes Anderson', role: 'DIRECTOR' },
			{ name: 'Wes Anderson', role: 'WRITER' },
			{ name: 'Hugo Guinness', role: 'WRITER' }
		]
	},
	{
		title: 'Everything Everywhere All at Once',
		synopsis:
			'A laundromat owner in the middle of a tax audit learns she can borrow skills from her lives in other universes, just as all of them are threatened.',
		releaseDate: '2022-03-25',
		runtimeMinutes: 139,
		genres: ['Science Fiction', 'Comedy'],
		poster: true,
		cast: [
			{ name: 'Michelle Yeoh', character: 'Evelyn Quan Wang' },
			{ name: 'Ke Huy Quan', character: 'Waymond Wang' },
			{ name: 'Jamie Lee Curtis', character: 'Deirdre Beaubeirdre' }
		],
		creators: [
			{ name: 'Daniel Kwan', role: 'DIRECTOR' },
			{ name: 'Daniel Scheinert', role: 'DIRECTOR' },
			{ name: 'Daniel Kwan', role: 'WRITER' },
			{ name: 'Daniel Scheinert', role: 'WRITER' }
		]
	},
	{
		title: 'The Matrix',
		synopsis:
			'A programmer learns that the world he lives in is a simulation, and joins the few who have escaped it.',
		releaseDate: '1999-03-31',
		runtimeMinutes: 136,
		genres: ['Science Fiction', 'Action'],
		poster: false,
		cast: [
			{ name: 'Keanu Reeves', character: 'Neo' },
			{ name: 'Laurence Fishburne', character: 'Morpheus' },
			{ name: 'Carrie-Anne Moss', character: 'Trinity' }
		],
		creators: [
			{ name: 'Lana Wachowski', role: 'DIRECTOR' },
			{ name: 'Lilly Wachowski', role: 'DIRECTOR' },
			{ name: 'Lana Wachowski', role: 'WRITER' },
			{ name: 'Lilly Wachowski', role: 'WRITER' }
		]
	}
];
